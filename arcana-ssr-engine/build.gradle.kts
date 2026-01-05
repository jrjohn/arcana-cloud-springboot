plugins {
    `java-library`
    eclipse
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management)
}

group = "com.arcana.cloud"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    // Plugin API
    api(project(":arcana-plugin-api"))

    // Spring Framework
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // GraalJS for server-side JavaScript execution
    implementation("org.graalvm.polyglot:polyglot:23.1.1")
    implementation("org.graalvm.polyglot:js:23.1.1")

    // Redis for caching (optional)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Jackson for JSON
    implementation("org.springframework.boot:spring-boot-starter-json")

    // Utilities
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Eclipse configuration - filter out POM-only artifacts and use Java 24
eclipse {
    classpath {
        file {
            whenMerged {
                val cp = this as org.gradle.plugins.ide.eclipse.model.Classpath
                // Remove entries that are POM files (not JARs)
                cp.entries.removeAll { entry ->
                    entry is org.gradle.plugins.ide.eclipse.model.Library &&
                    (entry.path.endsWith(".pom") || entry.path.contains("js-community"))
                }
            }
        }
    }
    jdt {
        sourceCompatibility = JavaVersion.VERSION_24
        targetCompatibility = JavaVersion.VERSION_24
    }
}
