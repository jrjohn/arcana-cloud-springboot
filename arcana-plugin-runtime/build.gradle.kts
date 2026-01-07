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

    // Redis for distributed plugin registry
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Apache Felix OSGi Framework
    implementation("org.apache.felix:org.apache.felix.framework:7.0.5")
    implementation("org.apache.felix:org.apache.felix.scr:2.2.6")
    implementation("org.apache.felix:org.apache.felix.fileinstall:3.7.4")
    implementation("org.apache.felix:org.apache.felix.configadmin:1.9.26")

    // OSGi API
    implementation("org.osgi:osgi.core:8.0.0")
    implementation("org.osgi:org.osgi.service.component:1.5.1")
    implementation("org.osgi:org.osgi.service.component.annotations:1.5.1")
    implementation("org.osgi:org.osgi.service.event:1.4.1")

    // XML parsing for arcana-plugin.xml
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.0")
    runtimeOnly("org.glassfish.jaxb:jaxb-runtime:4.0.3")

    // Flyway for plugin migrations
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    // Utilities
    implementation("org.slf4j:slf4j-api")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

// Eclipse configuration - use Java 24 since Eclipse JDT doesn't support Java 25 yet
eclipse {
    jdt {
        sourceCompatibility = JavaVersion.VERSION_24
        targetCompatibility = JavaVersion.VERSION_24
    }
}
