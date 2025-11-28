plugins {
    `java-library`
    `maven-publish`
}

group = "com.arcana.cloud"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // OSGi API (compile-only for plugin developers)
    compileOnly("org.osgi:osgi.core:8.0.0")
    compileOnly("org.osgi:org.osgi.service.component.annotations:1.5.1")

    // Jakarta APIs
    compileOnly("jakarta.servlet:jakarta.servlet-api:6.0.0")
    compileOnly("jakarta.validation:jakarta.validation-api:3.0.2")

    // Optional Spring dependencies for convenience annotations
    compileOnly("org.springframework:spring-web:6.2.0")
    compileOnly("org.springframework:spring-context:6.2.0")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("Arcana Plugin API")
                description.set("API for developing Arcana Cloud plugins")
                url.set("https://github.com/jrjohn/arcana-cloud-springboot")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}
