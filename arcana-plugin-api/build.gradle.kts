plugins {
    `java-library`
    `maven-publish`
}

group = "com.arcana.cloud"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
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
