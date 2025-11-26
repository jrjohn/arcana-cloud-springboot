# AI Prompt: 使用 Java Spring Boot 實作 Arcana Cloud 企業級微服務架構

## 專案概述

請協助我使用 **Java Spring Boot** 實作一個企業級的微服務平台，參考 Arcana Cloud Python 專案的架構設計。此專案需要支援多種部署模式、雙協議通訊、以及完整的安全與測試機制。

---

## 核心架構要求

### 1. 三層 Clean Architecture

實作標準的 Clean Architecture，包含以下三層：

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Spring Boot Backend Application                      │
│  Spring Boot 4.0.0+ | Java 21+ | gRPC-first + HTTP REST (dual-protocol)  │
│                                                                          │
│  ┌──────────────┐      ┌──────────────┐      ┌──────────────┐          │
│  │ Controller   │─────▶│   Service    │─────▶│  Repository  │          │
│  │    Layer     │ gRPC │     Layer    │ gRPC │     Layer    │          │
│  │  (REST API)  │◀─────│  (Business)  │◀─────│  (Database)  │          │
│  └──────────────┘      └──────────────┘      └──────────────┘          │
│         │                     │                     │                   │
│         └─────────────────────┴─────────────────────┘                   │
│                               │                                         │
└───────────────────────────────┼─────────────────────────────────────────┘
                                │
                    ┌───────────┼──────────┐
                    │           │          │
             ┌──────▼───┐  ┌────▼────┐  ┌──▼──────┐
             │  MySQL   │  │  Redis  │  │ RabbitMQ│
             │ Database │  │  Cache  │  │ Workers │
             └──────────┘  └─────────┘  └─────────┘
```

**層級職責：**

1. **Controller Layer (API Gateway)**
   - HTTP REST endpoints (`@RestController`)
   - Request validation (`@Valid`, Bean Validation)
   - JWT authentication (`Spring Security`)
   - Response serialization (Jackson)
   - OpenAPI/Swagger documentation

2. **Service Layer (Business Logic)**
   - Business rules implementation
   - Domain logic orchestration
   - Transaction management (`@Transactional`)
   - Communication abstraction (gRPC vs HTTP)
   - Service interfaces (`@Service`)

3. **Repository Layer (Data Access)**
   - Database operations (`Spring Data JPA`)
   - Caching strategy (`@Cacheable`, Redis)
   - Query building (JPA Criteria API)
   - Data persistence (JPA Repositories)

---

### 2. 三種部署模式

專案必須支援三種部署架構：

#### **Mode 1: Monolithic (單體模式)**
- 所有層級運行在單一 Spring Boot 應用中
- 適用於：開發環境、小型部署
- 優點：簡單、低延遲、易於除錯
- 配置：`deployment.mode=monolithic`

#### **Mode 2: Layered (分層模式) - 推薦**
- Controller、Service、Repository 各自獨立的 Spring Boot 應用
- 層級間透過 gRPC 或 HTTP REST 通訊
- 適用於：生產環境、中型應用
- 優點：獨立擴展、更好的故障隔離
- 配置：
  ```properties
  deployment.mode=layered
  deployment.layer=controller  # or service, repository
  communication.protocol=grpc  # or http
  ```

#### **Mode 3: Microservices (微服務模式)**
- 細粒度服務拆分（User Service、Auth Service 等）
- 每個服務獨立部署、獨立資料庫
- 適用於：企業級、高擴展性需求
- 優點：最大化擴展性、獨立部署、多語言支援
- 配置：
  ```properties
  deployment.mode=microservices
  service.name=user-service  # or auth-service, etc.
  ```

---

### 3. 雙協議通訊支援

**重要：gRPC 為預設協議（效能提升 2.78 倍）**

#### **gRPC 通訊（預設，高效能）**
- 使用 Protocol Buffers 進行序列化
- HTTP/2 多路複用
- 雙向串流支援
- 效能優勢：
  - 平均速度提升：2.78x
  - 點查詢：6.30x 更快
  - 寫入操作：1.27x 更快

**技術實作：**
- 以 Gradle 取代 Maven xml 結構如下
```xml
<!-- pom.xml dependencies -->
<dependency>
    <groupId>net.devh</groupId>
    <artifactId>spring-grpc-server-spring-boot-starter</artifactId>
    <version>1.0.0-RC1</version>
</dependency>
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-protobuf</artifactId>
    <version>1.68.0</version>
</dependency>
```

#### **HTTP REST 通訊（備選，易於除錯）**
- 標準 RESTful API (JSON)
- OpenAPI 3.0 文檔
- 適用於外部客戶端、瀏覽器相容

**配置切換：**
```properties
# application.properties
communication.protocol=grpc  # or http
service.url=localhost:9090   # gRPC port
service.rest.url=http://localhost:8081
repository.url=localhost:9091
```

---

## 技術棧要求

### 核心框架
- **Java**: 21+ (LTS, 支援 Virtual Threads)
- **Spring Boot**: 3.3+ (最新穩定版)
- **Spring Data JPA**: 3.3+ (Repository pattern)
- **Spring Security**: 6.3+ (OAuth2 + JWT)

### 通訊層
- **gRPC (預設)**: `spring-grpc-server-spring-boot-starter`
- **HTTP REST**: Spring WebMVC
- **Protocol Buffers**: `protobuf-java`

### 認證與安全
- **OAuth2 + JWT**: Spring Security OAuth2 Resource Server
- **Password Hashing**: BCrypt (Spring Security)
- **CORS**: `@CrossOrigin` 配置
- **Input Validation**: Bean Validation (`@Valid`, `@NotNull`, etc.)

### 資料與快取
- **Database**: MySQL 8.0+ (透過 JPA)
- **Cache**: Redis 7.0+ (Spring Data Redis)
- **Migration**: Flyway 或 Liquibase
- **Connection Pool**: HikariCP

### 容器與編排
- **Docker**: Multi-stage builds
- **Docker Compose**: 3種部署模式
- **Kubernetes**: Deployment, Service, Ingress, HPA

### 測試與品質
- **JUnit 5**: 單元測試
- **Spring Boot Test**: Integration tests
- **Testcontainers**: Database/Redis 容器測試
- **MockMvc**: Controller 層測試
- **WireMock**: HTTP/gRPC mock server
- **JaCoCo**: 程式碼覆蓋率
- **Checkstyle**: 程式碼風格檢查

### 其他工具
- **Lombok**: 減少 boilerplate code
- **MapStruct**: DTO 映射
- **Micrometer**: Metrics 收集
- **Logback**: 結構化日誌

---

## 專案結構

請按照以下結構組織專案：

```
arcana-cloud-java/
├── pom.xml                                   # Maven 依賴管理
├── README.md                                 # 專案說明文檔
├── .env.example                              # 環境變數範本
│
├── src/main/java/com/arcana/cloud/
│   ├── ArcanaCloudApplication.java          # Spring Boot 主程式
│   ├── config/                              # 配置類
│   │   ├── SecurityConfig.java              # Spring Security 配置
│   │   ├── RedisConfig.java                 # Redis 配置
│   │   ├── GrpcConfig.java                  # gRPC 配置
│   │   ├── JpaConfig.java                   # JPA 配置
│   │   └── SwaggerConfig.java               # API 文檔配置
│   │
│   ├── controller/                          # Controller Layer
│   │   ├── AuthController.java              # 認證端點
│   │   ├── UserController.java              # 用戶管理端點
│   │   └── PublicUserController.java        # 公開用戶端點
│   │
│   ├── service/                             # Service Layer
│   │   ├── interfaces/                      # Service 介面
│   │   │   ├── UserService.java
│   │   │   └── AuthService.java
│   │   ├── impl/                            # Service 實作
│   │   │   ├── UserServiceImpl.java
│   │   │   └── AuthServiceImpl.java
│   │   ├── grpc/                            # gRPC Service 實作
│   │   │   ├── UserGrpcService.java
│   │   │   └── AuthGrpcService.java
│   │   └── client/                          # 服務間通訊客戶端
│   │       ├── GrpcUserServiceClient.java
│   │       └── HttpUserServiceClient.java
│   │
│   ├── repository/                          # Repository Layer
│   │   ├── UserRepository.java              # JPA Repository
│   │   ├── OAuthTokenRepository.java
│   │   └── grpc/                            # gRPC Repository 服務
│   │       └── UserRepositoryGrpcService.java
│   │
│   ├── entity/                              # JPA Entities
│   │   ├── User.java
│   │   └── OAuthToken.java
│   │
│   ├── dto/                                 # Data Transfer Objects
│   │   ├── request/
│   │   │   ├── UserCreateRequest.java
│   │   │   ├── UserUpdateRequest.java
│   │   │   ├── LoginRequest.java
│   │   │   └── RegisterRequest.java
│   │   └── response/
│   │       ├── UserResponse.java
│   │       ├── AuthResponse.java
│   │       └── ApiResponse.java
│   │
│   ├── mapper/                              # DTO Mappers (MapStruct)
│   │   └── UserMapper.java
│   │
│   ├── security/                            # Security 相關
│   │   ├── JwtTokenProvider.java            # JWT Token 生成與驗證
│   │   ├── JwtAuthenticationFilter.java     # JWT 過濾器
│   │   └── UserPrincipal.java               # 用戶主體
│   │
│   ├── exception/                           # 自定義異常
│   │   ├── ResourceNotFoundException.java
│   │   ├── UnauthorizedException.java
│   │   ├── ValidationException.java
│   │   └── GlobalExceptionHandler.java      # 全局異常處理
│   │
│   └── util/                                # 工具類
│       ├── ResponseUtil.java
│       └── ValidationUtil.java
│
├── src/main/proto/                          # Protocol Buffers 定義
│   ├── user_service.proto
│   ├── auth_service.proto
│   └── common.proto
│
├── src/main/resources/
│   ├── application.properties               # 主配置檔
│   ├── application-monolithic.properties    # 單體模式配置
│   ├── application-layered.properties       # 分層模式配置
│   ├── application-microservices.properties # 微服務模式配置
│   ├── application-dev.properties           # 開發環境
│   ├── application-prod.properties          # 生產環境
│   └── db/migration/                        # Flyway 資料庫遷移
│       └── V1__Initial_schema.sql
│
├── src/test/java/com/arcana/cloud/
│   ├── integration/                         # 整合測試
│   │   ├── MonolithicModeTest.java
│   │   ├── LayeredModeTest.java
│   │   └── MicroservicesModeTest.java
│   ├── controller/                          # Controller 測試
│   │   ├── AuthControllerTest.java
│   │   └── UserControllerTest.java
│   ├── service/                             # Service 測試
│   │   ├── UserServiceTest.java
│   │   └── AuthServiceTest.java
│   └── repository/                          # Repository 測試
│       └── UserRepositoryTest.java
│
├── deployment/                              # 部署配置
│   ├── monolithic/
│   │   ├── Dockerfile
│   │   ├── docker-compose.yml
│   │   └── README.md
│   ├── layered/
│   │   ├── Dockerfile.controller
│   │   ├── Dockerfile.service
│   │   ├── Dockerfile.repository
│   │   ├── docker-compose.yml
│   │   └── README.md
│   └── kubernetes/
│       ├── namespace.yaml
│       ├── configmap.yaml
│       ├── secrets.yaml
│       ├── controller-deployment.yaml
│       ├── service-deployment.yaml
│       ├── repository-deployment.yaml
│       ├── services.yaml
│       ├── ingress.yaml
│       └── README.md
│
└── scripts/                                 # 腳本
    ├── start-monolithic.sh
    ├── start-layered.sh
    ├── start-microservices.sh
    └── generate-proto.sh
```

---

## 核心功能實作要求

### 1. 依賴注入（Dependency Injection）

使用 Spring 的 DI 機制，支援運行時切換通訊協議：

```java
@Configuration
public class ServiceConfiguration {

    @Value("${communication.protocol:grpc}")
    private String protocol;

    @Value("${deployment.mode:monolithic}")
    private String deploymentMode;

    @Bean
    @ConditionalOnProperty(name = "communication.protocol", havingValue = "grpc")
    public UserService grpcUserService() {
        if ("monolithic".equals(deploymentMode)) {
            // Monolithic mode: direct implementation
            return new UserServiceImpl(userRepository);
        } else {
            // Layered/Microservices: gRPC client
            return new GrpcUserServiceClient();
        }
    }

    @Bean
    @ConditionalOnProperty(name = "communication.protocol", havingValue = "http", matchIfMissing = true)
    public UserService httpUserService() {
        if ("monolithic".equals(deploymentMode)) {
            return new UserServiceImpl(userRepository);
        } else {
            return new HttpUserServiceClient();
        }
    }
}
```

### 2. JWT 認證與授權

```java
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:3600000}") // 1 hour
    private Long accessTokenExpiration;

    @Value("${jwt.refresh.expiration:2592000000}") // 30 days
    private Long refreshTokenExpiration;

    public String generateAccessToken(UserPrincipal userPrincipal) {
        return Jwts.builder()
            .setSubject(userPrincipal.getId().toString())
            .claim("username", userPrincipal.getUsername())
            .claim("role", userPrincipal.getRole())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
            .signWith(SignatureAlgorithm.HS256, jwtSecret)
            .compact();
    }

    public String generateRefreshToken(UserPrincipal userPrincipal) {
        // Similar implementation with longer expiration
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
            .setSigningKey(jwtSecret)
            .parseClaimsJws(token)
            .getBody();
        return Long.parseLong(claims.getSubject());
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        String token = getJwtFromRequest(request);

        if (token != null && tokenProvider.validateToken(token)) {
            Long userId = tokenProvider.getUserIdFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserById(userId);

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
                );

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

### 3. gRPC 服務實作

**Protocol Buffer 定義 (`user_service.proto`):**
```protobuf
syntax = "proto3";

package arcana.cloud;

option java_multiple_files = true;
option java_package = "com.arcana.cloud.grpc";
option java_outer_classname = "UserServiceProto";

service UserService {
  rpc GetUser(GetUserRequest) returns (UserResponse);
  rpc CreateUser(CreateUserRequest) returns (UserResponse);
  rpc UpdateUser(UpdateUserRequest) returns (UserResponse);
  rpc DeleteUser(DeleteUserRequest) returns (DeleteUserResponse);
  rpc ListUsers(ListUsersRequest) returns (ListUsersResponse);
}

message GetUserRequest {
  int64 user_id = 1;
}

message CreateUserRequest {
  string username = 1;
  string email = 2;
  string password = 3;
  string first_name = 4;
  string last_name = 5;
}

message UserResponse {
  int64 id = 1;
  string username = 2;
  string email = 3;
  string first_name = 4;
  string last_name = 5;
  string role = 6;
  bool is_active = 7;
  string created_at = 8;
}

message ListUsersRequest {
  int32 page = 1;
  int32 size = 2;
}

message ListUsersResponse {
  repeated UserResponse users = 1;
  int32 total_count = 2;
}

message DeleteUserRequest {
  int64 user_id = 1;
}

message DeleteUserResponse {
  bool success = 1;
  string message = 2;
}
```

**gRPC Server 實作:**
```java
@GrpcService
public class UserGrpcServiceImpl extends UserServiceGrpc.UserServiceImplBase {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Override
    public void getUser(GetUserRequest request,
                        StreamObserver<UserResponse> responseObserver) {
        try {
            User user = userService.getUserById(request.getUserId());
            UserResponse response = userMapper.toGrpcResponse(user);

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription("User not found")
                .asRuntimeException());
        }
    }

    @Override
    public void createUser(CreateUserRequest request,
                          StreamObserver<UserResponse> responseObserver) {
        try {
            User user = userMapper.fromGrpcRequest(request);
            User createdUser = userService.createUser(user);
            UserResponse response = userMapper.toGrpcResponse(createdUser);

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to create user")
                .asRuntimeException());
        }
    }

    @Override
    public void listUsers(ListUsersRequest request,
                         StreamObserver<ListUsersResponse> responseObserver) {
        try {
            Page<User> users = userService.getUsers(
                request.getPage(),
                request.getSize()
            );

            ListUsersResponse response = ListUsersResponse.newBuilder()
                .addAllUsers(users.stream()
                    .map(userMapper::toGrpcResponse)
                    .collect(Collectors.toList()))
                .setTotalCount((int) users.getTotalElements())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to list users")
                .asRuntimeException());
        }
    }
}
```

**gRPC Client 實作:**
```java
@Service
@ConditionalOnProperty(name = "deployment.mode", havingValue = "layered")
public class GrpcUserServiceClient implements UserService {

    @Value("${service.grpc.url:localhost:9090}")
    private String serviceUrl;

    private ManagedChannel channel;
    private UserServiceGrpc.UserServiceBlockingStub stub;

    @PostConstruct
    public void init() {
        this.channel = ManagedChannelBuilder.forTarget(serviceUrl)
            .usePlaintext()
            .build();
        this.stub = UserServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Override
    public User getUserById(Long userId) {
        GetUserRequest request = GetUserRequest.newBuilder()
            .setUserId(userId)
            .build();

        UserResponse response = stub.getUser(request);
        return userMapper.fromGrpcResponse(response);
    }

    @Override
    public User createUser(User user) {
        CreateUserRequest request = userMapper.toGrpcCreateRequest(user);
        UserResponse response = stub.createUser(request);
        return userMapper.fromGrpcResponse(response);
    }

    @Override
    public Page<User> getUsers(int page, int size) {
        ListUsersRequest request = ListUsersRequest.newBuilder()
            .setPage(page)
            .setSize(size)
            .build();

        ListUsersResponse response = stub.listUsers(request);
        return userMapper.fromGrpcListResponse(response);
    }
}
```

### 4. REST Controller 實作

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<User> users = userService.getUsers(page, size);
        Page<UserResponse> response = users.map(userMapper::toResponse);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isOwner(#id)")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable Long id) {

        User user = userService.getUserById(id);
        UserResponse response = userMapper.toResponse(user);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody UserCreateRequest request) {

        User user = userMapper.fromCreateRequest(request);
        User createdUser = userService.createUser(user);
        UserResponse response = userMapper.toResponse(createdUser);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "User created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isOwner(#id)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request) {

        User user = userMapper.fromUpdateRequest(request);
        User updatedUser = userService.updateUser(id, user);
        UserResponse response = userMapper.toResponse(updatedUser);

        return ResponseEntity.ok(
            ApiResponse.success(response, "User updated successfully")
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(
            ApiResponse.success(null, "User deleted successfully")
        );
    }
}
```

### 5. Repository 層實作

```java
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "first_name", length = 50)
    private String firstName;

    @Column(name = "last_name", length = 50)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.role = :role AND u.isActive = true")
    List<User> findActiveUsersByRole(@Param("role") UserRole role);
}
```

### 6. 全局異常處理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            ResourceNotFoundException ex) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(
            UnauthorizedException ex) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() != null
                    ? error.getDefaultMessage()
                    : "Validation error"
            ));

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Validation failed", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex) {
        log.error("Unexpected error occurred", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("Internal server error"));
    }
}
```

---

## 配置檔範例

### `application.properties`

```properties
# Application
spring.application.name=arcana-cloud-java
server.port=8080

# Deployment Configuration
deployment.mode=monolithic
deployment.layer=controller
communication.protocol=grpc

# Database
spring.datasource.url=jdbc:mysql://localhost:3306/arcana_cloud?useSSL=false&serverTimezone=UTC
spring.datasource.username=arcana
spring.datasource.password=arcana_pass
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
spring.jpa.properties.hibernate.format_sql=true

# Flyway
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true

# Redis
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.password=
spring.redis.timeout=2000ms

# JWT
jwt.secret=your-256-bit-secret-key-change-this-in-production
jwt.expiration=3600000
jwt.refresh.expiration=2592000000

# gRPC
grpc.server.port=9090
service.grpc.url=localhost:9090
repository.grpc.url=localhost:9091

# REST Service URLs
service.rest.url=http://localhost:8081
repository.rest.url=http://localhost:8082

# Logging
logging.level.root=INFO
logging.level.com.arcana.cloud=DEBUG
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n

# Actuator
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=always
```

---

## 測試要求

### 1. 整合測試範例

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
public class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("arcana_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.0")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void testCreateUser_Success() throws Exception {
        String adminToken = generateAdminToken();

        UserCreateRequest request = UserCreateRequest.builder()
            .username("testuser")
            .email("test@example.com")
            .password("SecurePass123")
            .firstName("Test")
            .lastName("User")
            .build();

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("testuser"))
            .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    void testGetUser_NotFound() throws Exception {
        String adminToken = generateAdminToken();

        mockMvc.perform(get("/api/v1/users/999")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    private String generateAdminToken() {
        User admin = User.builder()
            .id(1L)
            .username("admin")
            .role(UserRole.ADMIN)
            .build();

        UserPrincipal principal = UserPrincipal.create(admin);
        return tokenProvider.generateAccessToken(principal);
    }
}
```

### 2. 測試覆蓋率要求
- 單元測試覆蓋率：≥ 80%
- 整合測試覆蓋率：≥ 90%
- 所有三種部署模式都要通過完整測試套件
- 使用 JaCoCo 生成覆蓋率報告

---

## Docker 部署配置

### Monolithic Mode Dockerfile

```dockerfile
# Multi-stage build
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

ENV DEPLOYMENT_MODE=monolithic
ENV JAVA_OPTS="-Xmx512m -Xms256m"

EXPOSE 8080 9090

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### Layered Mode Docker Compose

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root_pass
      MYSQL_DATABASE: arcana_cloud
      MYSQL_USER: arcana
      MYSQL_PASSWORD: arcana_pass
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  redis:
    image: redis:7.0-alpine
    ports:
      - "6379:6379"

  repository:
    build:
      context: .
      dockerfile: Dockerfile.repository
    environment:
      DEPLOYMENT_MODE: layered
      DEPLOYMENT_LAYER: repository
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/arcana_cloud
      SPRING_REDIS_HOST: redis
    ports:
      - "8082:8080"
      - "9091:9090"
    depends_on:
      - mysql
      - redis

  service:
    build:
      context: .
      dockerfile: Dockerfile.service
    environment:
      DEPLOYMENT_MODE: layered
      DEPLOYMENT_LAYER: service
      COMMUNICATION_PROTOCOL: grpc
      REPOSITORY_GRPC_URL: repository:9091
    ports:
      - "8081:8080"
      - "9090:9090"
    depends_on:
      - repository

  controller:
    build:
      context: .
      dockerfile: Dockerfile.controller
    environment:
      DEPLOYMENT_MODE: layered
      DEPLOYMENT_LAYER: controller
      COMMUNICATION_PROTOCOL: grpc
      SERVICE_GRPC_URL: service:9090
    ports:
      - "8080:8080"
    depends_on:
      - service

volumes:
  mysql_data:
```

---

## Kubernetes 部署範例

### Service Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: arcana-service
  namespace: arcana-cloud
spec:
  replicas: 3
  selector:
    matchLabels:
      app: arcana-service
      layer: service
  template:
    metadata:
      labels:
        app: arcana-service
        layer: service
    spec:
      containers:
      - name: service
        image: arcana-cloud-java:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          name: http
        - containerPort: 9090
          name: grpc
        env:
        - name: DEPLOYMENT_MODE
          value: "layered"
        - name: DEPLOYMENT_LAYER
          value: "service"
        - name: COMMUNICATION_PROTOCOL
          value: "grpc"
        - name: REPOSITORY_GRPC_URL
          value: "arcana-repository:9090"
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            configMapKeyRef:
              name: arcana-config
              key: database.url
        - name: SPRING_REDIS_HOST
          valueFrom:
            configMapKeyRef:
              name: arcana-config
              key: redis.host
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: arcana-secrets
              key: jwt.secret
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5

---
apiVersion: v1
kind: Service
metadata:
  name: arcana-service
  namespace: arcana-cloud
spec:
  selector:
    app: arcana-service
    layer: service
  ports:
  - name: http
    port: 8080
    targetPort: 8080
  - name: grpc
    port: 9090
    targetPort: 9090
  type: ClusterIP

---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: arcana-service-hpa
  namespace: arcana-cloud
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: arcana-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

---

## API 文檔要求

使用 SpringDoc OpenAPI 3.0：

```java
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Arcana Cloud Java API")
                .version("1.0.0")
                .description("Enterprise-grade cloud platform with gRPC-first architecture")
                .contact(new Contact()
                    .name("Arcana Cloud Team")
                    .email("support@arcana-cloud.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
```

---

## 效能指標要求

專案需要提供效能測試報告，比較 HTTP REST 與 gRPC 的效能差異：

### 預期效能指標

| 操作類型 | HTTP (ms) | gRPC (ms) | 加速比 |
|---------|-----------|-----------|--------|
| 點查詢（Get User by ID） | ~9.0 | ~1.5 | 6.0x |
| 列表查詢（List 20 Users） | ~11.0 | ~9.0 | 1.2x |
| 寫入操作（Create User） | ~16.0 | ~12.0 | 1.3x |
| **平均** | ~12.5 | ~7.5 | **2.5x** |

### 效能測試工具
- **JMeter**: HTTP REST 負載測試
- **ghz**: gRPC 負載測試
- **Gatling**: 完整情境測試

---

## 程式碼品質標準

### Checkstyle 配置
- 遵循 Google Java Style Guide
- 最大行長：120 字元
- 縮排：4 空格
- 方法最大長度：50 行

### SonarQube 指標
- 程式碼覆蓋率：≥ 80%
- 程式碼重複率：≤ 3%
- 技術債務比率：≤ 5%
- 無 Critical/Blocker 問題

---

## 輸出交付要求

請協助我完成以下交付成果：

1. **完整專案原始碼**
   - 按照上述結構組織
   - 包含所有必要的配置檔案
   - Maven pom.xml 完整配置

2. **README.md 文檔**
   - 專案介紹與架構說明
   - 快速開始指南
   - API 文檔連結
   - 部署指南

3. **部署配置**
   - Dockerfile（3種模式）
   - docker-compose.yml（3種模式）
   - Kubernetes manifests（完整）

4. **測試套件**
   - 單元測試（每個層級）
   - 整合測試（3種部署模式）
   - 效能測試腳本

5. **文檔**
   - 架構設計文檔
   - API 使用指南
   - 部署運維手冊
   - 效能測試報告

---

## 額外需求

1. **可觀測性**
   - 使用 Micrometer 收集 metrics
   - Prometheus 格式輸出
   - Grafana dashboard 配置
   - 結構化日誌（JSON 格式）

2. **安全性**
   - OWASP Top 10 防護
   - SQL Injection 防護（JPA）
   - XSS 防護（輸入驗證）
   - CSRF Token 驗證
   - Rate Limiting（Redis）

3. **CI/CD**
   - GitHub Actions workflow
   - Maven 自動化建置
   - Docker image 自動推送
   - Kubernetes 自動部署

---

## 參考資源

- Python 原專案：https://github.com/jrjohn/arcana-cloud-python
- Spring Boot 文檔：https://spring.io/projects/spring-boot
- gRPC Java 文檔：https://grpc.io/docs/languages/java/
- Spring Security 文檔：https://spring.io/projects/spring-security

---

## 結論

這是一個企業級的 Java Spring Boot 專案，需要完整實作 Clean Architecture、支援三種部署模式、雙協議通訊（gRPC + HTTP REST）、完整的安全機制、以及高測試覆蓋率。

請按照上述規格，協助我逐步實作這個專案，並確保程式碼品質符合企業標準。
