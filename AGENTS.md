# AGENTS.md — arcana-cloud-springboot

Instructions for any AI coding agent (Claude Code, Codex, Cursor, Copilot, Gemini…) working
in this repository **or in any product created from it**. `CLAUDE.md`, `GEMINI.md` and
`.github/copilot-instructions.md` all point here; this file is the single source.

> **You MUST NOT report a task as done until `tools/arch-check.sh` exits 0 and the CI unit-test
> lane (§5) is green.** Breaking the layering costs a red build: the pre-commit hook
> (`tools/githooks/pre-commit`) rejects new files outside the layer packages and `*Impl`
> references outside `config/`, and Jenkins stage "Architecture Qube" fails the merge below
> score 90.

## 0. First command in a fresh clone

Run `tools/install-hooks.sh` once. Git does not install hooks on clone; until you run it the
pre-commit check does not exist and only CI catches a flat layout — after the work is
written. `tools/new-app.sh` runs it for you.

## 1. Starting something new

| Situation | Do this | Never do this |
|---|---|---|
| New service product | `tools/new-app.sh <name> <dest-dir> [base-package]` — copies the whole repo, renames the base package `com.arcana.cloud`, Gradle/jar/image names, Sonar key and the Jenkins/CI container names, resets the version, installs the hook | Spring Initializr plus a few copied packages |
| New business feature (entity + CRUD API) | `tools/new-feature.sh <Name>` — one vertical slice: controller → service + impl → repository + impl → DAO + MyBatis and JPA impls → entity, DTOs, MapStruct mapper, Flyway migrations (mysql + postgresql), unit tests per layer | A controller that injects a DAO, or a new class next to the `*Application` class |
| Feature over gRPC, MongoDB or the layered deployment | After `new-feature.sh`, mirror the User feature by hand: `src/main/proto/user_service.proto`, `service/grpc/UserGrpcService.java`, `service/client/Grpc/HttpUserServiceClient.java`, `repository/impl/grpc/`, `dao/impl/mongodb/` | Call a DAO from a gRPC service |

Copying parts instead of `new-app.sh` loses this file, the hook and the CI gate, so nothing
tells the next agent the rules exist.

## 2. Layers — where a file goes

`<base>` is `src/main/java/com/arcana/cloud` in the template (`new-app.sh` renames it).
Spring wires beans by component scan; which implementation is active is chosen by
`@ConditionalOnProperty` / `@ConditionalOnExpression` on `database.orm`, `database.type`,
`deployment.layer` and `repository.mode`.

| Layer | Path (package) | Contains | May depend on |
|---|---|---|---|
| Boundary (REST) | `<base>/controller/`, `controller/internal/` | `@RestController`: validate, delegate to a service interface, wrap in `ApiResponse` | service interfaces, `dto/`, `security/` |
| Boundary (gRPC) | `<base>/service/grpc/` (servers), `service/client/` (remote service clients); messages in `src/main/proto/` | gRPC services and the HTTP/gRPC clients used when `deployment.layer=controller` | service interfaces, repository interfaces (repository-layer server only), `dto/` |
| Service | interface `<base>/service/<X>Service.java`; impl `service/impl/<X>ServiceImpl.java` | business rules, transactions (`@Transactional`), DTO ↔ entity mapping | repository interfaces, `mapper/`, `dto/`, `entity/`, `exception/` |
| Repository | interface `<base>/repository/<X>Repository.java`; impls `repository/impl/{mybatis,jpa,mongodb,grpc}/` | domain data access; picks the DAO | DAO interfaces, `entity/` |
| DAO | interface `<base>/dao/<X>Dao.java` (extends `BaseDao`); impls `dao/impl/mybatis/` (+ `mapper/` and `src/main/resources/mybatis/mapper/*.xml`), `dao/impl/jpa/` (+ `repository/`), `dao/impl/mongodb/` | the only code that talks to a database | `entity/`, `document/` |
| Model | `<base>/entity/` (JPA/MyBatis), `document/` (MongoDB), `dto/request/`, `dto/response/`, `mapper/` (MapStruct) | data shapes and their conversion | nothing above them |
| Cross-cutting | `<base>/config/`, `security/`, `exception/`, `scheduler/` (Quartz, has its own controller/service/mapper) | Spring configuration, JWT, error handling, jobs | any layer interface |
| Framework modules | `arcana-plugin-api/`, `arcana-plugin-runtime/`, `arcana-ssr-engine/`, `plugins/arcana-audit-plugin/` | OSGi plugin system, SSR, sample plugin | each other only; never the application module |

Direction is one way: controller → service → repository → DAO → entity. A lower layer never
uses a higher one. Worked example of every layer: the User feature — `UserController` →
`UserService`/`UserServiceImpl` → `UserRepository`/`UserRepositoryMybatisImpl` →
`UserDao`/`UserDaoMybatisImpl` → `entity/User`. Schema changes are new Flyway files in
`src/main/resources/db/migration/{mysql,postgresql}/`.

## 3. Rules

| # | Rule | Enforced by |
|---|---|---|
| R1 | Every service, repository and DAO is an interface `XService` / `XRepository` / `XDao` implemented in an `impl/` sub-package by `XServiceImpl` / `XRepository<Orm>Impl` / `XDao<Orm>Impl`. | arch-qube "Interface/Impl Colocation", "Implementation Naming Convention" |
| R2 | Only Spring configuration (`<base>/config/`) and code inside `impl/` packages may name a concrete `*Impl` class or import from an `.impl.` package. Everyone else injects the interface by constructor (`@RequiredArgsConstructor` + `private final`). Tests are exempt. | arch-qube "Only DI Container Imports Impl"; pre-commit hook |
| R3 | Controllers call services only; services call repositories only; only DAOs touch a database (MyBatis mapper, JPA repository, `MongoTemplate`). | arch-qube "Backend Layer Chain", "Controller Cannot Use DAO", "Service Cannot Access DB Directly", "Repository Cannot Call Service", "Layer Direction Enforcement" |
| R4 | A new `.java` file under `<base>` goes in a layer package: `controller`, `service`, `repository`, `dao`, `entity`, `document`, `dto`, `mapper`, `config`, `security`, `exception` or `scheduler`. Nothing new next to the `*Application` class or outside `<base>`. | pre-commit hook |
| R5 | A controller method only validates, delegates and wraps: ≤ 20 lines, no business logic. | arch-qube "No Business Logic in Boundary" |
| R6 | New controllers take and return DTOs only; the service maps with a MapStruct mapper in `mapper/`. (`UserController`/`JobController` still import entities — legacy, do not copy.) | arch-qube "DTO/Entity Separation" |
| R7 | `@Transactional` belongs on service methods; DAOs may add it for their own reads. | arch-qube "Transaction at Service Layer Only" |
| R8 | Every new controller, service, repository, DAO and mapper has a unit test that runs without a database (Mockito, as `new-feature.sh` generates). | arch-qube "Test Coverage Threshold"; SonarQube quality gate (Jenkins stage "SonarQube Analysis") |
| R9 | Schema changes are a new `V<n>__*.sql` in both `db/migration/mysql` and `db/migration/postgresql`, never an edit of an applied file. | Flyway checksum validation at startup |
| R10 | The JDK is 25: `build.gradle.kts` toolchain and the `Dockerfile.ci` builder image change together in one PR. | CI image build (Jenkins stage "Docker Compose Build") |

## 4. Worked example

`tools/new-feature.sh Invoice` creates `entity/Invoice`, `dao/InvoiceDao` +
`dao/impl/mybatis/InvoiceDaoMybatisImpl` + `dao/impl/jpa/InvoiceDaoJpaImpl`,
`repository/InvoiceRepository` + `repository/impl/InvoiceRepositoryImpl`,
`service/InvoiceService` + `service/impl/InvoiceServiceImpl`, `controller/InvoiceController`
(`/api/v1/invoices`), DTOs, `mapper/InvoiceMapper`, migrations and six test classes. It is
active in monolithic mode on MySQL/PostgreSQL with either ORM. Add a field in the order
migration → entity → MyBatis XML → DTOs → tests.

## 5. Before you say "done"

Run these, in this order, and paste the last line of each into your summary:

```bash
tools/arch-check.sh     # same image, --framework springboot, --threshold 90 as CI; must print "arch-check: PASS"
docker compose -f docker-compose.ci.yml build                        # Jenkins "Docker Compose Build": gradle build -x test
docker compose -f docker-compose.test.yml run --build --rm test      # Jenkins "Unit Tests": gradle :test :jacocoTestReport -Dspring.profiles.active=test
```

The test lane needs the external Docker network `devops_default`
(`docker network create devops_default` once on a machine without it) and takes about 30
minutes. With a local JDK 25 and Gradle 9.2 the same lane is
`gradle :test :jacocoTestReport --no-daemon -Dspring.profiles.active=test`.

## 6. Architecture check (arch-qube)

arch-qube (https://github.com/jrjohn/arcana-arch-qube; usage skill
https://github.com/jrjohn/arcana-skills/tree/main/arch-qube-skill) scans the source for the
layering rules above and fails below a score threshold.

- **Run it:** `tools/arch-check.sh`. It uses `ghcr.io/jrjohn/arch-qube:<v>` (public,
  multi-arch), then `arcana.boo/arcana/arch-qube:<v>` — no `:latest` fallback — with `<v>`
  from `tools/arch-qube.version` (0.5.7), the same file the Jenkinsfile reads, and the same
  `--framework springboot --threshold 90` and exclusions as the Jenkins stage.
- **Without Docker:** `pipx run --spec "git+https://github.com/jrjohn/arcana-arch-qube@v0.5.7" arch-qube scan . --framework springboot --no-ai --ci --format json,markdown -o arch-qube-reports --threshold 90`
  (or `pip install "git+https://github.com/jrjohn/arcana-arch-qube@v0.5.7"` into a venv). Use the
  tag that matches `tools/arch-qube.version`.
- **Exit codes:** 0 = pass; 2 = could not check (no Docker, image unavailable) — that is NOT
  a pass; anything else = fail.
- **Pass:** score ≥ threshold **and** zero violations in every CRITICAL rule — a 99 can
  still FAIL.
- **`--ci` prints nothing on failure.** Rerun without `--ci` (or open `arch-qube.md`) to see why.
- **Report:** `arch-qube-reports/arch-qube.md` and `arch-qube.json`. The score alone is not
  enough: read every rule row and the violations list. A rule under "Not evaluated"
  (NOT EVALUATED) says nothing about the code, so it is not a pass for a layer you touched. In the JSON, check
  `files_checked` for each rule — a rule that checked 0 files proved nothing. A project
  without layer packages scores 0 on "Layer Direction Enforcement".
- **False positive:** open an issue on https://github.com/jrjohn/arcana-arch-qube with the
  report attached. Never lower `--threshold`, add exclusions or move code to dodge a rule.

## 7. Reference

- Optional detail: `arcana-springboot-developer-skill`
  (https://github.com/jrjohn/arcana-skills/tree/main/arcana-springboot-developer-skill).
  This file is the source of truth: if the skill and this file disagree, this file wins.
