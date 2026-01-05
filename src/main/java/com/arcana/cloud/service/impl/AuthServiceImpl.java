package com.arcana.cloud.service.impl;

import com.arcana.cloud.dto.request.LoginRequest;
import com.arcana.cloud.dto.request.RefreshTokenRequest;
import com.arcana.cloud.dto.request.RegisterRequest;
import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.entity.OAuthToken;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.UnauthorizedException;
import com.arcana.cloud.exception.ValidationException;
import com.arcana.cloud.mapper.UserMapper;
import com.arcana.cloud.repository.interfaces.OAuthTokenRepository;
import com.arcana.cloud.repository.interfaces.UserRepository;
import com.arcana.cloud.security.JwtTokenProvider;
import com.arcana.cloud.security.UserPrincipal;
import com.arcana.cloud.service.interfaces.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AuthService implementation with direct database access.
 * Active in: monolithic mode OR service layer of layered mode.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
@ConditionalOnExpression("'${deployment.layer:}' == '' or '${deployment.layer:}' == 'service'")
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final OAuthTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UserMapper userMapper;

    @Value("${jwt.expiration:3600000}")
    private Long accessTokenExpiration;

    @Value("${jwt.refresh.expiration:2592000000}")
    private Long refreshTokenExpiration;

    @Override
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user with username: {}", request.getUsername());

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new ValidationException("Passwords do not match");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ValidationException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException("Email already exists");
        }

        User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with id: {}", savedUser.getId());

        return generateAuthResponse(savedUser);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsernameOrEmail());

        User user = userRepository.findByUsernameOrEmail(
                request.getUsernameOrEmail(),
                request.getUsernameOrEmail()
            )
            .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        if (!user.getIsActive()) {
            throw new UnauthorizedException("Account is disabled");
        }

        log.info("User logged in successfully: {}", user.getUsername());
        return generateAuthResponse(user);
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Refreshing token");

        if (!tokenProvider.validateToken(request.getRefreshToken())) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        Long userId = tokenProvider.getUserIdFromToken(request.getRefreshToken());
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!user.getIsActive()) {
            throw new UnauthorizedException("Account is disabled");
        }

        OAuthToken existingToken = tokenRepository.findByRefreshToken(request.getRefreshToken())
            .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (existingToken.getIsRevoked()) {
            throw new UnauthorizedException("Token has been revoked");
        }

        existingToken.setIsRevoked(true);
        tokenRepository.save(existingToken);

        log.info("Token refreshed successfully for user: {}", user.getUsername());
        return generateAuthResponse(user);
    }

    @Override
    public void logout(String accessToken) {
        log.info("Logging out user");
        tokenRepository.revokeByAccessToken(accessToken);
    }

    @Override
    public void logoutAll(Long userId) {
        log.info("Logging out all sessions for user: {}", userId);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ValidationException("User not found"));
        tokenRepository.revokeAllTokensByUser(user);
    }

    private AuthResponse generateAuthResponse(User user) {
        UserPrincipal principal = UserPrincipal.create(user);

        String accessToken = tokenProvider.generateAccessToken(principal);
        String refreshToken = tokenProvider.generateRefreshToken(principal);

        LocalDateTime now = LocalDateTime.now();
        OAuthToken token = OAuthToken.builder()
            .user(user)
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresAt(now.plusSeconds(accessTokenExpiration / 1000))
            .refreshExpiresAt(now.plusSeconds(refreshTokenExpiration / 1000))
            .build();

        tokenRepository.save(token);

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(accessTokenExpiration / 1000)
            .user(userMapper.toResponse(user))
            .build();
    }
}
