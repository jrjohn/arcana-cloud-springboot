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
        languageVersion = JavaLanguageVersion.of(17)
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

dependencyManagement {
    imports {
        mavenBom("org.springframework.grpc:spring-grpc-dependencies:$springGrpcVersion")
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
    implementation(libs.bundles.flyway)

    // API Documentation
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Utilities
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)
    annotationProcessor(libs.lombok.mapstruct.binding)

    // Metrics
    implementation(libs.micrometer.registry.prometheus)

    // Annotation processing for Jakarta and javax (for gRPC generated code)
    implementation(libs.jakarta.annotation.api)
    implementation(libs.javax.annotation.api)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.spring.boot.data.jpa.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.bundles.testcontainers)
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
    // Fork every 20 test classes to balance isolation vs overhead
    forkEvery = 20
    // Set timeouts to prevent infinite hangs
    systemProperty("junit.jupiter.execution.timeout.default", "60s")
    // Ensure Gradle test executor exits cleanly
    jvmArgs("-XX:+HeapDumpOnOutOfMemoryError")
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
        file {
            whenMerged {
                val cp = this as org.gradle.plugins.ide.eclipse.model.Classpath
                cp.entries.forEach { entry ->
                    if (entry is org.gradle.plugins.ide.eclipse.model.SourceFolder) {
                        entry.entryAttributes["optional"] = "true"
                    }
                }
            }
        }
    }
}
