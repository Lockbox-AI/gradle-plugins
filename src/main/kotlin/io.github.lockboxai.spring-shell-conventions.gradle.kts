/**
 * Lockbox Spring Shell Conventions Plugin
 * 
 * This convention plugin defines shared configuration for Spring Shell CLI applications.
 * It builds on top of lockbox.gradle.plugins.spring-boot-conventions and adds:
 * - Spring Shell plugin application
 * - Shell-specific packaging and configuration
 * - Interactive CLI setup
 * 
 * Dependency versions are managed by the framework-platform BOM.
 * 
 * Future enhancements will include:
 * - Shell-specific testing configurations
 * - Command auto-completion setup
 * - Shell-specific packaging conventions
 */

plugins {
    // Apply the base Spring Boot conventions plugin first
    id("io.github.lockboxai.spring-boot-conventions")
}

// ========================================
// Spring Shell Configuration
// ========================================

// Add Spring Shell dependencies
// Spring Shell is a library, not a Gradle plugin
dependencies {
    // Spring Shell starter dependency
    // Version is managed by framework-platform BOM
    implementation("org.springframework.shell:spring-shell-starter")
}
