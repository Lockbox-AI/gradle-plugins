/**
 * Lockbox Gradle Plugins Build Configuration
 * 
 * Builds and publishes reusable Gradle plugins for Java/Kotlin projects with AWS integration.
 * 
 * Plugins Provided:
 * - aws-environment: AWS authentication, role assumption, and environment setup
 * - codeartifact-repositories: CodeArtifact Maven repository configuration
 * - codeartifact-repositories-settings: CodeArtifact plugin repository configuration (settings)
 * - java-conventions: Base Java configuration with quality tools and testing
 * - module-conventions: Lockbox module configuration (extends java-conventions)
 * - spring-boot-conventions: Spring Boot application configuration
 * - spring-shell-conventions: Spring Shell CLI application configuration
 */

import java.time.Instant

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = project.property("group") as String
description = "Lockbox Gradle Plugins - Reusable build conventions and AWS integration"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // AWS SDK BOM for version management
    implementation(platform(libs.aws.sdk.bom))
    
    // AWS SDK dependencies
    implementation(libs.aws.s3)
    implementation(libs.aws.apache.client)
    implementation(libs.aws.codeartifact)
    implementation(libs.aws.sts)
    implementation(libs.aws.ssm)
    
    // Gradle plugin dependencies for convention plugins
    implementation(libs.spotless.plugin)
    implementation(libs.lombok.plugin)
    implementation(libs.spotbugs.plugin)
    implementation(libs.spring.boot.plugin)
    implementation(libs.dependency.management.plugin)
}

kotlin {
    jvmToolchain(21)
}

// ========================================
// Gradle Plugin Declarations
// ========================================
/**
 * Declares programmatic Gradle plugins (Kotlin classes) for registration.
 * 
 * Precompiled script plugins (.gradle.kts files) are automatically discovered
 * by the kotlin-dsl plugin and do not need explicit declaration here.
 */
gradlePlugin {
    plugins {
        create("awsEnvironment") {
            id = "io.github.lockboxai.aws-environment"
            displayName = "Lockbox AWS Environment"
            description = "AWS authentication, role assumption, and environment setup"
            implementationClass = "com.lockbox.gradle.AwsEnvironmentPlugin"
            tags.set(listOf("aws", "codeartifact", "authentication"))
        }
        create("codeartifactRepositories") {
            id = "io.github.lockboxai.codeartifact-repositories"
            displayName = "Lockbox CodeArtifact Repositories"
            description = "Configure Maven repositories for AWS CodeArtifact"
            implementationClass = "com.lockbox.gradle.CodeArtifactRepositoriesPlugin"
            tags.set(listOf("aws", "codeartifact", "repositories"))
        }
        create("codeartifactRepositoriesSettings") {
            id = "io.github.lockboxai.codeartifact-repositories-settings"
            displayName = "Lockbox CodeArtifact Repositories (Settings)"
            description = "Configure plugin repositories for AWS CodeArtifact in settings"
            implementationClass = "com.lockbox.gradle.CodeArtifactRepositoriesSettingsPlugin"
            tags.set(listOf("aws", "codeartifact", "repositories"))
        }
    }
}

// ========================================
// JAR Manifest Configuration
// ========================================
tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Lockbox AI",
            "Built-By" to System.getProperty("user.name"),
            "Built-JDK" to System.getProperty("java.version"),
            "Build-Timestamp" to Instant.now().toString()
        )
    }
}

// ========================================
// Publishing Configuration
// ========================================
java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    repositories {
        mavenLocal()
    }
}
