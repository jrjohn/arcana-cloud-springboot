plugins {
    java
    id("biz.aQute.bnd.builder") version "6.4.0"
}

group = "com.arcana.plugin"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
    // Plugin API (compile-only since host provides it)
    compileOnly(project(":arcana-plugin-api"))

    // OSGi API
    compileOnly("org.osgi:osgi.core:8.0.0")
    compileOnly("org.osgi:org.osgi.service.component.annotations:1.5.1")

    // Jakarta APIs (provided by host)
    compileOnly("jakarta.persistence:jakarta.persistence-api:3.1.0")
    compileOnly("jakarta.validation:jakarta.validation-api:3.0.2")

    // Spring (provided by host)
    compileOnly("org.springframework:spring-web:6.2.0")
    compileOnly("org.springframework:spring-context:6.2.0")
    compileOnly("org.springframework.data:spring-data-jpa:3.4.0")

    // Logging
    compileOnly("org.slf4j:slf4j-api:2.0.9")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Bundle-ManifestVersion" to "2",
            "Bundle-SymbolicName" to "com.arcana.plugin.audit",
            "Bundle-Version" to project.version,
            "Bundle-Name" to "Arcana Audit Plugin",
            "Bundle-Description" to "Comprehensive audit logging for Arcana Cloud",
            "Bundle-Vendor" to "Arcana Cloud",
            "Bundle-Activator" to "com.arcana.plugin.audit.Activator",
            "Arcana-Plugin-Key" to "com.arcana.plugin.audit",
            "Arcana-Plugin-Name" to "Audit Log Plugin",
            "Arcana-Min-Platform-Version" to "1.0.0",
            "Import-Package" to listOf(
                "com.arcana.cloud.plugin.api;version=\"[1.0,2.0)\"",
                "com.arcana.cloud.plugin.extension;version=\"[1.0,2.0)\"",
                "com.arcana.cloud.plugin.event;version=\"[1.0,2.0)\"",
                "com.arcana.cloud.plugin.lifecycle;version=\"[1.0,2.0)\"",
                "org.osgi.framework;version=\"[1.8,2.0)\"",
                "jakarta.persistence;version=\"[3.0,4.0)\"",
                "org.slf4j;version=\"[2.0,3.0)\"",
                "org.springframework.web.bind.annotation;resolution:=optional",
                "org.springframework.http;resolution:=optional"
            ).joinToString(","),
            "Export-Package" to "com.arcana.plugin.audit.api;version=\"1.0.0\""
        )
    }
}

// Create bundle task for OSGi
tasks.register<Copy>("bundle") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    into("${rootProject.projectDir}/plugins")
}
