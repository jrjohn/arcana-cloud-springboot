package com.arcana.cloud.controller;

import com.arcana.cloud.dto.response.ApiResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.mapper.UserMapper;
import com.arcana.cloud.security.SecurityService;
import com.arcana.cloud.security.UserPrincipal;
import com.arcana.cloud.service.interfaces.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Current User", description = "Current user profile APIs")
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnExpression("'${deployment.layer:}' == '' or '${deployment.layer:}' == 'controller'")
public class PublicUserController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final SecurityService securityService;

    @GetMapping
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UserPrincipal principal = securityService.getCurrentUser();
        if (principal == null) {
            return ResponseEntity.ok(ApiResponse.error("User not authenticated"));
        }

        var user = userService.getUserById(principal.getId());
        UserResponse response = userMapper.toResponse(user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
