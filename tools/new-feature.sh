#!/usr/bin/env bash
# Generate ONE vertical feature slice the way the template's User feature is layered:
#
#   REST controller → service interface + impl (DTO in / DTO out, MapStruct mapper)
#   → repository interface + impl → DAO interface (extends BaseDao) + MyBatis impl
#   (mapper interface + XML) + JPA impl (Spring Data repository) → entity
#   + Flyway migrations (mysql + postgresql) + unit tests at every layer.
#
#   tools/new-feature.sh Invoice            # PascalCase, singular
#   tools/new-feature.sh PurchaseOrder
#
# Spring wires everything by component scan; no registration file is edited. The slice is
# active in monolithic mode on MySQL/PostgreSQL with either ORM (database.orm=mybatis|jpa).
# MongoDB, deployment.layer=controller|service|repository and repository.mode=grpc are a
# manual step: mirror the User feature (AGENTS.md §1).
# Fields: the slice starts with `name` + `description`. Add a field in the order
# migration → entity → MyBatis XML → DTOs → service tests.
set -euo pipefail

[ $# -eq 1 ] || { echo "usage: $0 <FeatureName>   (PascalCase, singular, e.g. Invoice)" >&2; exit 1; }
P="$1"
[[ "$P" =~ ^[A-Z][a-zA-Z0-9]*$ ]] || { echo "FeatureName must be PascalCase (e.g. Invoice, PurchaseOrder)" >&2; exit 1; }
c="$(printf '%s' "${P:0:1}" | tr 'A-Z' 'a-z')${P:1}"                          # purchaseOrder
s="$(printf '%s' "$P" | perl -pe 's/(?<=[a-z0-9])([A-Z])/_$1/g; $_=lc')"       # purchase_order
TABLE="${s}s"
URL="$(printf '%s' "$TABLE" | tr '_' '-')"
case "$s" in user|user_role|o_auth_token|oauth_token|auth|job|job_execution_history|plugin|api_response|paged_response)
    echo "'$P' collides with a template class" >&2; exit 1 ;; esac

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# ---- Anchors. Each is something the generated code relies on; a template change that
# removes one must break this script loudly instead of generating code that cannot work.
die() { echo "new-feature: $*" >&2; exit 1; }
APP_FILE="$(git ls-files 'src/main/java/*Application.java' | head -1)"
[ -n "$APP_FILE" ] || die "no src/main/java/**/*Application.java (the @SpringBootApplication class) — anchor missing"
MAIN="${APP_FILE%/*}"                                   # src/main/java/com/arcana/cloud
PKG="$(printf '%s' "${MAIN#src/main/java/}" | tr '/' '.')"
TEST="src/test/java/${MAIN#src/main/java/}"
RES=src/main/resources
need_file() { [ -f "$1" ] || die "anchor missing: $1"; }
need_grep() { grep -q -- "$2" "$1" 2>/dev/null || die "anchor missing: '$2' in $1"; }
need_file "$MAIN/dao/BaseDao.java"
need_file "$MAIN/dto/response/ApiResponse.java"
need_file "$MAIN/exception/ResourceNotFoundException.java"
need_file "$MAIN/security/JwtTokenProvider.java"
need_file "$MAIN/security/UserPrincipal.java"
need_file "$MAIN/repository/UserRepository.java"
need_grep "$MAIN/config/MyBatisConfig.java" "$PKG.dao.impl.mybatis.mapper"
need_grep "$MAIN/config/JpaConfig.java" "$PKG.dao.impl.jpa.repository"
need_grep "$RES/application.properties" "mybatis.mapper-locations=classpath:mybatis/mapper/\*.xml"
need_grep "$RES/application.properties" "mybatis.type-aliases-package=$PKG.entity"
[ -d "$RES/db/migration/mysql" ] && [ -d "$RES/db/migration/postgresql" ] || die "anchor missing: $RES/db/migration/{mysql,postgresql}"
[ ! -e "$MAIN/entity/$P.java" ] || die "feature '$P' already exists ($MAIN/entity/$P.java)"

# Next Flyway version = highest V<n> in either dialect + 1 (both dialects stay in step).
V=$(( $(ls "$RES"/db/migration/mysql "$RES"/db/migration/postgresql | perl -ne 'print "$1\n" if /^V(\d+)__/' | sort -n | tail -1) + 1 ))

# render <dest>: stdin template → dest, placeholders substituted.
render() {
    mkdir -p "$(dirname "$1")"
    sed -e "s/__PKG__/$PKG/g" -e "s/__P__/$P/g" -e "s/__c__/$c/g" -e "s/__TABLE__/$TABLE/g" \
        -e "s/__URL__/$URL/g" > "$1"
    echo "  + $1"
}

# Active in monolithic mode on a relational database (see header).
COND="'\${database.type:}' != 'mongodb' and '\${repository.mode:direct}' != 'grpc' and '\${deployment.layer:}' == ''"
COND_ESC="$(printf '%s' "$COND" | sed 's/[\/&$]/\\&/g')"
cond() { sed "s/__COND__/$COND_ESC/g"; }

echo "new-feature: $P → package $PKG, table $TABLE, /api/v1/$URL, migration V$V"

# ======================================================================= entity
render "$MAIN/entity/$P.java" <<'EOF'
package __PKG__.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * __P__ entity. Persisted by the DAO layer only; never returned by a controller.
 */
@Entity
@Table(name = "__TABLE__")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class __P__ implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
EOF

# ======================================================================= migrations
render "$RES/db/migration/mysql/V${V}__create_${TABLE}.sql" <<'EOF'
-- __P__ feature (generated by tools/new-feature.sh)
CREATE TABLE IF NOT EXISTS __TABLE__ (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
EOF
render "$RES/db/migration/postgresql/V${V}__create_${TABLE}.sql" <<'EOF'
-- __P__ feature (generated by tools/new-feature.sh)
CREATE TABLE IF NOT EXISTS __TABLE__ (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
EOF

# ======================================================================= DAO
render "$MAIN/dao/${P}Dao.java" <<'EOF'
package __PKG__.dao;

import __PKG__.entity.__P__;

/**
 * Data access for {@link __P__}. One implementation per ORM under dao/impl/.
 */
public interface __P__Dao extends BaseDao<__P__, Long> {
}
EOF

render "$MAIN/dao/impl/mybatis/mapper/${P}Mapper.java" <<'EOF'
package __PKG__.dao.impl.mybatis.mapper;

import __PKG__.entity.__P__;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis mapper for {@link __P__}; SQL lives in resources/mybatis/mapper/__P__Mapper.xml.
 */
@Mapper
public interface __P__Mapper {

    int insert(__P__ entity);

    int update(__P__ entity);

    Optional<__P__> findById(@Param("id") Long id);

    boolean existsById(@Param("id") Long id);

    List<__P__> findAll();

    List<__P__> findAllWithPagination(@Param("offset") long offset, @Param("limit") int limit);

    long count();

    int deleteById(@Param("id") Long id);

    int deleteAll();
}
EOF

render "$RES/mybatis/mapper/${P}Mapper.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<!-- Generated by tools/new-feature.sh -->
<mapper namespace="__PKG__.dao.impl.mybatis.mapper.__P__Mapper">

    <resultMap id="__P__ResultMap" type="__PKG__.entity.__P__">
        <id property="id" column="id"/>
        <result property="name" column="name"/>
        <result property="description" column="description"/>
        <result property="createdAt" column="created_at"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <insert id="insert" parameterType="__PKG__.entity.__P__" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO __TABLE__ (name, description, created_at, updated_at)
        VALUES (#{name}, #{description}, COALESCE(#{createdAt}, NOW()), COALESCE(#{updatedAt}, NOW()))
    </insert>

    <update id="update" parameterType="__PKG__.entity.__P__">
        UPDATE __TABLE__
        SET name = #{name},
            description = #{description},
            updated_at = NOW()
        WHERE id = #{id}
    </update>

    <select id="findById" resultMap="__P__ResultMap">
        SELECT * FROM __TABLE__ WHERE id = #{id}
    </select>

    <select id="existsById" resultType="boolean">
        SELECT COUNT(*) > 0 FROM __TABLE__ WHERE id = #{id}
    </select>

    <select id="findAll" resultMap="__P__ResultMap">
        SELECT * FROM __TABLE__ ORDER BY created_at DESC
    </select>

    <select id="findAllWithPagination" resultMap="__P__ResultMap">
        SELECT * FROM __TABLE__ ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}
    </select>

    <select id="count" resultType="long">
        SELECT COUNT(*) FROM __TABLE__
    </select>

    <delete id="deleteById">
        DELETE FROM __TABLE__ WHERE id = #{id}
    </delete>

    <delete id="deleteAll">
        DELETE FROM __TABLE__
    </delete>
</mapper>
EOF

render "$MAIN/dao/impl/mybatis/${P}DaoMybatisImpl.java" <<'EOF'
package __PKG__.dao.impl.mybatis;

import __PKG__.dao.__P__Dao;
import __PKG__.dao.impl.mybatis.mapper.__P__Mapper;
import __PKG__.entity.__P__;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis implementation of {@link __P__Dao} (database.orm=mybatis, the default).
 */
@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional
@ConditionalOnExpression("'${database.orm:mybatis}' == 'mybatis' and '${database.type:}' != 'mongodb'")
public class __P__DaoMybatisImpl implements __P__Dao {

    private final __P__Mapper __c__Mapper;

    @Override
    public __P__ save(__P__ entity) {
        log.debug("MyBatis DAO: Saving __c__: {}", entity.getName());
        if (entity.getId() == null) {
            __c__Mapper.insert(entity);
        } else {
            __c__Mapper.update(entity);
        }
        return entity;
    }

    @Override
    public List<__P__> saveAll(Iterable<__P__> entities) {
        List<__P__> result = new ArrayList<>();
        for (__P__ entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<__P__> findById(Long id) {
        return __c__Mapper.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return __c__Mapper.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<__P__> findAll() {
        return __c__Mapper.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<__P__> findAll(Pageable pageable) {
        long total = __c__Mapper.count();
        List<__P__> content = __c__Mapper.findAllWithPagination(pageable.getOffset(), pageable.getPageSize());
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return __c__Mapper.count();
    }

    @Override
    public void deleteById(Long id) {
        __c__Mapper.deleteById(id);
    }

    @Override
    public void delete(__P__ entity) {
        if (entity.getId() != null) {
            __c__Mapper.deleteById(entity.getId());
        }
    }

    @Override
    public void deleteAll() {
        __c__Mapper.deleteAll();
    }

    @Override
    public void deleteAll(Iterable<__P__> entities) {
        for (__P__ entity : entities) {
            delete(entity);
        }
    }
}
EOF

render "$MAIN/dao/impl/jpa/repository/${P}JpaRepository.java" <<'EOF'
package __PKG__.dao.impl.jpa.repository;

import __PKG__.entity.__P__;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link __P__}; used only by __P__DaoJpaImpl.
 */
@Repository
@ConditionalOnProperty(name = "database.orm", havingValue = "jpa")
public interface __P__JpaRepository extends JpaRepository<__P__, Long> {
}
EOF

render "$MAIN/dao/impl/jpa/${P}DaoJpaImpl.java" <<'EOF'
package __PKG__.dao.impl.jpa;

import __PKG__.dao.__P__Dao;
import __PKG__.dao.impl.jpa.repository.__P__JpaRepository;
import __PKG__.entity.__P__;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA implementation of {@link __P__Dao} (database.orm=jpa).
 */
@Repository
@RequiredArgsConstructor
@Transactional
@ConditionalOnProperty(name = "database.orm", havingValue = "jpa")
public class __P__DaoJpaImpl implements __P__Dao {

    private final __P__JpaRepository __c__JpaRepository;

    @Override
    public __P__ save(__P__ entity) {
        return __c__JpaRepository.save(entity);
    }

    @Override
    public List<__P__> saveAll(Iterable<__P__> entities) {
        return __c__JpaRepository.saveAll(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<__P__> findById(Long id) {
        return __c__JpaRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return __c__JpaRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<__P__> findAll() {
        return __c__JpaRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<__P__> findAll(Pageable pageable) {
        return __c__JpaRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return __c__JpaRepository.count();
    }

    @Override
    public void deleteById(Long id) {
        __c__JpaRepository.deleteById(id);
    }

    @Override
    public void delete(__P__ entity) {
        __c__JpaRepository.delete(entity);
    }

    @Override
    public void deleteAll() {
        __c__JpaRepository.deleteAll();
    }

    @Override
    public void deleteAll(Iterable<__P__> entities) {
        __c__JpaRepository.deleteAll(entities);
    }
}
EOF

# ======================================================================= repository
render "$MAIN/repository/${P}Repository.java" <<'EOF'
package __PKG__.repository;

import __PKG__.entity.__P__;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Domain-level access to {@link __P__}. Services depend on this interface, never on a DAO.
 */
public interface __P__Repository {

    __P__ save(__P__ entity);

    Optional<__P__> findById(Long id);

    boolean existsById(Long id);

    Page<__P__> findAll(Pageable pageable);

    void deleteById(Long id);
}
EOF

cond <<'EOF' | render "$MAIN/repository/impl/${P}RepositoryImpl.java"
package __PKG__.repository.impl;

import __PKG__.dao.__P__Dao;
import __PKG__.entity.__P__;
import __PKG__.repository.__P__Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository over {@link __P__Dao}; the active DAO implementation is chosen by database.orm.
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnExpression("__COND__")
public class __P__RepositoryImpl implements __P__Repository {

    private final __P__Dao __c__Dao;

    @Override
    public __P__ save(__P__ entity) {
        return __c__Dao.save(entity);
    }

    @Override
    public Optional<__P__> findById(Long id) {
        return __c__Dao.findById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return __c__Dao.existsById(id);
    }

    @Override
    public Page<__P__> findAll(Pageable pageable) {
        return __c__Dao.findAll(pageable);
    }

    @Override
    public void deleteById(Long id) {
        __c__Dao.deleteById(id);
    }
}
EOF

# ======================================================================= DTOs + mapper
render "$MAIN/dto/request/${P}CreateRequest.java" <<'EOF'
package __PKG__.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for creating a __P__.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("java:S1068")
public class __P__CreateRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}
EOF

render "$MAIN/dto/request/${P}UpdateRequest.java" <<'EOF'
package __PKG__.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for updating a __P__; null fields are left unchanged.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("java:S1068")
public class __P__UpdateRequest {

    @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}
EOF

render "$MAIN/dto/response/${P}Response.java" <<'EOF'
package __PKG__.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API representation of a __P__.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("java:S1068")
public class __P__Response {

    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
EOF

render "$MAIN/mapper/${P}Mapper.java" <<'EOF'
package __PKG__.mapper;

import __PKG__.dto.request.__P__CreateRequest;
import __PKG__.dto.request.__P__UpdateRequest;
import __PKG__.dto.response.__P__Response;
import __PKG__.entity.__P__;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper between __P__ and its DTOs. Used by the service layer.
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface __P__Mapper {

    __P__Response toResponse(__P__ entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    __P__ fromCreateRequest(__P__CreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromRequest(__P__UpdateRequest request, @MappingTarget __P__ entity);
}
EOF

# ======================================================================= service
render "$MAIN/service/${P}Service.java" <<'EOF'
package __PKG__.service;

import __PKG__.dto.request.__P__CreateRequest;
import __PKG__.dto.request.__P__UpdateRequest;
import __PKG__.dto.response.__P__Response;
import org.springframework.data.domain.Page;

/**
 * __P__ business operations. Takes and returns DTOs; entities stay below this layer.
 */
public interface __P__Service {

    __P__Response create(__P__CreateRequest request);

    __P__Response getById(Long id);

    Page<__P__Response> list(int page, int size);

    __P__Response update(Long id, __P__UpdateRequest request);

    void delete(Long id);
}
EOF

cond <<'EOF' | render "$MAIN/service/impl/${P}ServiceImpl.java"
package __PKG__.service.impl;

import __PKG__.dto.request.__P__CreateRequest;
import __PKG__.dto.request.__P__UpdateRequest;
import __PKG__.dto.response.__P__Response;
import __PKG__.entity.__P__;
import __PKG__.exception.ResourceNotFoundException;
import __PKG__.mapper.__P__Mapper;
import __PKG__.repository.__P__Repository;
import __PKG__.service.__P__Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link __P__Service}. Business rules for __P__ go here, not in the controller.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
@ConditionalOnExpression("__COND__")
public class __P__ServiceImpl implements __P__Service {

    private final __P__Repository __c__Repository;
    private final __P__Mapper __c__Mapper;

    @Override
    public __P__Response create(__P__CreateRequest request) {
        __P__ saved = __c__Repository.save(__c__Mapper.fromCreateRequest(request));
        log.info("__P__ created with id: {}", saved.getId());
        return __c__Mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public __P__Response getById(Long id) {
        return __c__Mapper.toResponse(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<__P__Response> list(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return __c__Repository.findAll(pageRequest).map(__c__Mapper::toResponse);
    }

    @Override
    public __P__Response update(Long id, __P__UpdateRequest request) {
        __P__ existing = find(id);
        __c__Mapper.updateFromRequest(request, existing);
        return __c__Mapper.toResponse(__c__Repository.save(existing));
    }

    @Override
    public void delete(Long id) {
        if (!__c__Repository.existsById(id)) {
            throw new ResourceNotFoundException("__P__", "id", id);
        }
        __c__Repository.deleteById(id);
        log.info("__P__ deleted with id: {}", id);
    }

    private __P__ find(Long id) {
        return __c__Repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("__P__", "id", id));
    }
}
EOF

# ======================================================================= controller
cond <<'EOF' | render "$MAIN/controller/${P}Controller.java"
package __PKG__.controller;

import __PKG__.dto.request.__P__CreateRequest;
import __PKG__.dto.request.__P__UpdateRequest;
import __PKG__.dto.response.ApiResponse;
import __PKG__.dto.response.__P__Response;
import __PKG__.service.__P__Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST boundary for __P__: extract, delegate to {@link __P__Service}, wrap the DTO.
 */
@RestController
@RequestMapping("/api/v1/__URL__")
@RequiredArgsConstructor
@Tag(name = "__P__", description = "__P__ APIs")
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnExpression("__COND__")
public class __P__Controller {

    private final __P__Service __c__Service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List __URL__ with pagination")
    public ResponseEntity<ApiResponse<Page<__P__Response>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(__c__Service.list(page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get __P__ by ID")
    public ResponseEntity<ApiResponse<__P__Response>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(__c__Service.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a __P__")
    public ResponseEntity<ApiResponse<__P__Response>> create(@Valid @RequestBody __P__CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(__c__Service.create(request), "__P__ created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a __P__")
    public ResponseEntity<ApiResponse<__P__Response>> update(
            @PathVariable Long id, @Valid @RequestBody __P__UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(__c__Service.update(id, request), "__P__ updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a __P__")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        __c__Service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "__P__ deleted successfully"));
    }
}
EOF

# ======================================================================= tests
render "$TEST/service/${P}ServiceTest.java" <<'EOF'
package __PKG__.service;

import __PKG__.dto.request.__P__CreateRequest;
import __PKG__.dto.request.__P__UpdateRequest;
import __PKG__.dto.response.__P__Response;
import __PKG__.entity.__P__;
import __PKG__.exception.ResourceNotFoundException;
import __PKG__.mapper.__P__Mapper;
import __PKG__.repository.__P__Repository;
import __PKG__.service.impl.__P__ServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class __P__ServiceTest {

    @Mock
    private __P__Repository __c__Repository;

    @Spy
    private __P__Mapper __c__Mapper = Mappers.getMapper(__P__Mapper.class);

    @InjectMocks
    private __P__ServiceImpl __c__Service;

    private __P__ existing;

    @BeforeEach
    void setUp() {
        existing = __P__.builder().id(1L).name("first").description("desc").build();
    }

    @Test
    void create_savesAndReturnsDto() {
        when(__c__Repository.save(any(__P__.class))).thenAnswer(inv -> {
            __P__ e = inv.getArgument(0);
            e.setId(7L);
            return e;
        });

        __P__Response created = __c__Service.create(__P__CreateRequest.builder().name("new").build());

        assertEquals(7L, created.getId());
        assertEquals("new", created.getName());
    }

    @Test
    void getById_found() {
        when(__c__Repository.findById(1L)).thenReturn(Optional.of(existing));

        assertEquals("first", __c__Service.getById(1L).getName());
    }

    @Test
    void getById_missing_throws() {
        when(__c__Repository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> __c__Service.getById(2L));
    }

    @Test
    void list_mapsPage() {
        Page<__P__> page = new PageImpl<>(List.of(existing), PageRequest.of(0, 20), 1);
        when(__c__Repository.findAll(any(Pageable.class))).thenReturn(page);

        Page<__P__Response> result = __c__Service.list(0, 20);

        assertEquals(1, result.getTotalElements());
        assertEquals("first", result.getContent().get(0).getName());
    }

    @Test
    void update_changesOnlyGivenFields() {
        when(__c__Repository.findById(1L)).thenReturn(Optional.of(existing));
        when(__c__Repository.save(any(__P__.class))).thenAnswer(inv -> inv.getArgument(0));

        __P__Response updated = __c__Service.update(1L, __P__UpdateRequest.builder().name("renamed").build());

        assertEquals("renamed", updated.getName());
        assertEquals("desc", updated.getDescription());
    }

    @Test
    void delete_existing() {
        when(__c__Repository.existsById(1L)).thenReturn(true);

        __c__Service.delete(1L);

        verify(__c__Repository).deleteById(1L);
    }

    @Test
    void delete_missing_throws() {
        when(__c__Repository.existsById(3L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> __c__Service.delete(3L));
        verify(__c__Repository, never()).deleteById(3L);
    }
}
EOF

render "$TEST/repository/impl/${P}RepositoryImplTest.java" <<'EOF'
package __PKG__.repository.impl;

import __PKG__.dao.__P__Dao;
import __PKG__.entity.__P__;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class __P__RepositoryImplTest {

    @Mock
    private __P__Dao __c__Dao;

    @InjectMocks
    private __P__RepositoryImpl __c__Repository;

    @Test
    void delegatesToDao() {
        __P__ entity = __P__.builder().id(1L).name("n").build();
        PageRequest pageable = PageRequest.of(0, 10);
        Page<__P__> page = new PageImpl<>(List.of(entity), pageable, 1);
        when(__c__Dao.save(entity)).thenReturn(entity);
        when(__c__Dao.findById(1L)).thenReturn(Optional.of(entity));
        when(__c__Dao.existsById(1L)).thenReturn(true);
        when(__c__Dao.findAll(pageable)).thenReturn(page);

        assertThat(__c__Repository.save(entity)).isSameAs(entity);
        assertThat(__c__Repository.findById(1L)).contains(entity);
        assertThat(__c__Repository.existsById(1L)).isTrue();
        assertThat(__c__Repository.findAll(pageable)).isSameAs(page);
        __c__Repository.deleteById(1L);
        verify(__c__Dao).deleteById(1L);
    }
}
EOF

render "$TEST/dao/impl/mybatis/${P}DaoMybatisImplTest.java" <<'EOF'
package __PKG__.dao.impl.mybatis;

import __PKG__.dao.impl.mybatis.mapper.__P__Mapper;
import __PKG__.entity.__P__;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class __P__DaoMybatisImplTest {

    @Mock
    private __P__Mapper __c__Mapper;

    @InjectMocks
    private __P__DaoMybatisImpl __c__Dao;

    @Test
    void save_insertsNewAndUpdatesExisting() {
        __P__ fresh = __P__.builder().name("new").build();
        __P__ stored = __P__.builder().id(5L).name("old").build();

        __c__Dao.saveAll(List.of(fresh, stored));

        verify(__c__Mapper).insert(fresh);
        verify(__c__Mapper).update(stored);
    }

    @Test
    void findAll_pagesThroughMapper() {
        __P__ entity = __P__.builder().id(1L).name("n").build();
        when(__c__Mapper.count()).thenReturn(11L);
        when(__c__Mapper.findAllWithPagination(10L, 10)).thenReturn(List.of(entity));

        Page<__P__> page = __c__Dao.findAll(PageRequest.of(1, 10));

        assertThat(page.getTotalElements()).isEqualTo(11L);
        assertThat(page.getContent()).containsExactly(entity);
    }

    @Test
    void readsAndDeletesDelegate() {
        __P__ entity = __P__.builder().id(3L).name("n").build();
        when(__c__Mapper.findById(3L)).thenReturn(Optional.of(entity));
        when(__c__Mapper.existsById(3L)).thenReturn(true);
        when(__c__Mapper.findAll()).thenReturn(List.of(entity));
        when(__c__Mapper.count()).thenReturn(1L);

        assertThat(__c__Dao.findById(3L)).contains(entity);
        assertThat(__c__Dao.existsById(3L)).isTrue();
        assertThat(__c__Dao.findAll()).containsExactly(entity);
        assertThat(__c__Dao.count()).isEqualTo(1L);
        __c__Dao.deleteAll(List.of(entity));
        __c__Dao.deleteAll();
        verify(__c__Mapper).deleteById(3L);
        verify(__c__Mapper).deleteAll();
    }
}
EOF

render "$TEST/dao/impl/jpa/${P}DaoJpaImplTest.java" <<'EOF'
package __PKG__.dao.impl.jpa;

import __PKG__.dao.impl.jpa.repository.__P__JpaRepository;
import __PKG__.entity.__P__;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class __P__DaoJpaImplTest {

    @Mock
    private __P__JpaRepository __c__JpaRepository;

    @InjectMocks
    private __P__DaoJpaImpl __c__Dao;

    @Test
    void delegatesToSpringData() {
        __P__ entity = __P__.builder().id(1L).name("n").build();
        PageRequest pageable = PageRequest.of(0, 5);
        Page<__P__> page = new PageImpl<>(List.of(entity), pageable, 1);
        when(__c__JpaRepository.save(entity)).thenReturn(entity);
        when(__c__JpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(__c__JpaRepository.existsById(1L)).thenReturn(true);
        when(__c__JpaRepository.findAll()).thenReturn(List.of(entity));
        when(__c__JpaRepository.findAll(pageable)).thenReturn(page);
        when(__c__JpaRepository.count()).thenReturn(1L);

        assertThat(__c__Dao.save(entity)).isSameAs(entity);
        assertThat(__c__Dao.findById(1L)).contains(entity);
        assertThat(__c__Dao.existsById(1L)).isTrue();
        assertThat(__c__Dao.findAll()).containsExactly(entity);
        assertThat(__c__Dao.findAll(pageable)).isSameAs(page);
        assertThat(__c__Dao.count()).isEqualTo(1L);
        __c__Dao.deleteById(1L);
        __c__Dao.delete(entity);
        verify(__c__JpaRepository).deleteById(1L);
        verify(__c__JpaRepository).delete(entity);
    }
}
EOF

render "$TEST/mapper/${P}MapperTest.java" <<'EOF'
package __PKG__.mapper;

import __PKG__.dto.request.__P__CreateRequest;
import __PKG__.dto.request.__P__UpdateRequest;
import __PKG__.entity.__P__;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class __P__MapperTest {

    private final __P__Mapper mapper = Mappers.getMapper(__P__Mapper.class);

    @Test
    void createRequestToEntity_ignoresId() {
        __P__ entity = mapper.fromCreateRequest(__P__CreateRequest.builder().name("a").description("d").build());

        assertThat(entity.getId()).isNull();
        assertThat(entity.getName()).isEqualTo("a");
    }

    @Test
    void updateKeepsNullFields() {
        __P__ entity = __P__.builder().id(1L).name("a").description("keep").build();

        mapper.updateFromRequest(__P__UpdateRequest.builder().name("b").build(), entity);

        assertThat(entity.getName()).isEqualTo("b");
        assertThat(entity.getDescription()).isEqualTo("keep");
        assertThat(mapper.toResponse(entity).getId()).isEqualTo(1L);
    }
}
EOF

render "$TEST/controller/${P}ControllerTest.java" <<'EOF'
package __PKG__.controller;

import __PKG__.dto.request.__P__CreateRequest;
import __PKG__.dto.response.__P__Response;
import __PKG__.entity.User;
import __PKG__.entity.UserRole;
import __PKG__.repository.UserRepository;
import __PKG__.security.JwtTokenProvider;
import __PKG__.security.UserPrincipal;
import __PKG__.service.__P__Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class __P__ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockitoBean
    private __P__Service __c__Service;

    @MockitoBean
    private UserRepository userRepository;

    private String adminToken;

    @BeforeEach
    void setUp() {
        User admin = User.builder().id(99L).username("admin").email("admin@example.com")
            .password("x").role(UserRole.ADMIN).isActive(true).build();
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        adminToken = tokenProvider.generateAccessToken(UserPrincipal.create(admin));
    }

    @Test
    void list_asAdmin() throws Exception {
        __P__Response dto = __P__Response.builder().id(1L).name("first").build();
        when(__c__Service.list(anyInt(), anyInt()))
            .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/__URL__").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].name").value("first"));
    }

    @Test
    void list_withoutToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/__URL__"))
            .andExpect(status().isForbidden());
    }

    @Test
    void create_asAdmin() throws Exception {
        when(__c__Service.create(any(__P__CreateRequest.class)))
            .thenReturn(__P__Response.builder().id(2L).name("new").build());

        mockMvc.perform(post("/api/v1/__URL__")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(__P__CreateRequest.builder().name("new").build())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").value(2));
    }

    @Test
    void create_blankName_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/__URL__")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void delete_asAdmin() throws Exception {
        mockMvc.perform(delete("/api/v1/__URL__/5").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
        verify(__c__Service).delete(5L);
    }
}
EOF

echo "new-feature: done. Next: tools/arch-check.sh and the CI test lane (AGENTS.md §5)."
