import com.google.protobuf.gradle.*

plugins {
    java
    eclipse
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.protobuf)
    id("jacoco")
    id("checkstyle")
}

group = "com.arcana.cloud"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val grpcVersion = versionCatalog.findVersion("grpc").get().toString()
val protobufVersion = versionCatalog.findVersion("protobuf").get().toString()
val checkstyleVersion = versionCatalog.findVersion("checkstyle").get().toString()
val springGrpcVersion = versionCatalog.findVersion("spring-grpc").get().toString()

val springCloudVersion = versionCatalog.findVersion("spring-cloud").get().toString()

dependencyManagement {
    imports {
        mavenBom("org.springframework.grpc:spring-grpc-dependencies:$springGrpcVersion")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
    }
}

dependencies {
    // Spring Boot Starters
    implementation(libs.bundles.spring.boot.starters)

    // Jackson 3 (Spring Boot 4.0 default)
    implementation(libs.spring.boot.jackson)

    // gRPC Dependencies (Spring gRPC for Spring Boot 4.0)
    implementation(libs.bundles.grpc)

    // JWT
    implementation(libs.bundles.jjwt)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // Database
    runtimeOnly(libs.mysql.connector.j)
    runtimeOnly(libs.postgresql)
    implementation(libs.bundles.flyway)

    // MyBatis (default ORM)
    implementation(libs.mybatis.spring.boot.starter)

    // MongoDB (optional)
    implementation(libs.spring.boot.starter.data.mongodb)

    // Quartz Scheduler (distributed job scheduling)
    implementation("org.springframework.boot:spring-boot-starter-quartz")

    // API Documentation
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Utilities
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)
    annotationProcessor(libs.lombok.mapstruct.binding)

    // Metrics
    implementation(libs.micrometer.registry.prometheus)

    // Resilience4j Circuit Breaker
    implementation(libs.resilience4j.circuitbreaker)

    // Spring Cloud Config (optional - enable with spring.cloud.config.enabled=true)
    implementation(libs.spring.cloud.starter.config)

    // Annotation processing for Jakarta and javax (for gRPC generated code)
    implementation(libs.jakarta.annotation.api)
    implementation(libs.javax.annotation.api)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.spring.boot.data.jpa.test)
    testImplementation(libs.spring.boot.test.autoconfigure)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.mybatis.spring.boot.starter.test)
    testImplementation(libs.bundles.testcontainers)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testRuntimeOnly(libs.h2)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Plugin testing dependencies
    testImplementation(project(":arcana-plugin-api"))
    testImplementation(project(":arcana-plugin-runtime"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
    // Fork every test class to ensure complete isolation (prevents hangs)
    forkEvery = 1
    // Limit max parallel forks to prevent resource exhaustion
    maxParallelForks = 1
    // Set timeouts to prevent infinite hangs
    systemProperty("junit.jupiter.execution.timeout.default", "30s")
    // Ensure Gradle test executor exits cleanly + suppress Felix Unsafe warnings on Java 21+
    jvmArgs(
        "-XX:+HeapDumpOnOutOfMemoryError",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.cs=ALL-UNNAMED",
        "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED",
        "--add-opens=java.base/sun.security.util=ALL-UNNAMED",
        "--add-opens=java.base/sun.misc=ALL-UNNAMED"
    )
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}

checkstyle {
    toolVersion = checkstyleVersion
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = true
}

sourceSets {
    main {
        java {
            srcDirs(
                "build/generated/source/proto/main/java",
                "build/generated/source/proto/main/grpc"
            )
        }
    }
}

eclipse {
    classpath {
        // Ensure generated source folders are included
        file {
            whenMerged {
                val cp = this as org.gradle.plugins.ide.eclipse.model.Classpath
                // Remove any entries with non-existent paths
                cp.entries.removeAll { entry ->
                    entry is org.gradle.plugins.ide.eclipse.model.SourceFolder &&
                    !file(entry.path).exists()
                }
                // Fix JRE container to use default (Eclipse will use workspace JRE)
                cp.entries.filterIsInstance<org.gradle.plugins.ide.eclipse.model.Container>()
                    .filter { it.path.contains("JRE_CONTAINER") }
                    .forEach { container ->
                        container.path = "org.eclipse.jdt.launching.JRE_CONTAINER"
                    }
            }
        }
    }
    jdt {
        sourceCompatibility = JavaVersion.VERSION_24
        targetCompatibility = JavaVersion.VERSION_24
    }
}

// Set encoding for all projects
allprojects {
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    // Configure Eclipse encoding
    plugins.withType<EclipsePlugin> {
        eclipse {
            project {
                natures("org.eclipse.jdt.core.javanature")
            }
        }
    }
}

