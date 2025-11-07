# Lockbox Gradle Plugins

A collection of reusable Gradle plugins for building Java/Kotlin projects with AWS integration.

## Plugins Provided

- **aws-environment**: AWS authentication, role assumption, and environment setup
- **codeartifact-repositories**: Configure Maven repositories for AWS CodeArtifact
- **codeartifact-repositories-settings**: Configure plugin repositories for AWS CodeArtifact in settings
- **java-conventions**: Base Java configuration with quality tools and testing
- **module-conventions**: Lockbox module configuration (extends java-conventions)
- **spring-boot-conventions**: Spring Boot application configuration
- **spring-shell-conventions**: Spring Shell CLI application configuration

## Requirements

- Java 21+
- Gradle 8.11+

## Installation

Add the plugin repository and apply plugins in your `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.lockboxai.java-conventions") version "1.0.0"
}
```

## Configuration

### Required Properties

The `java-conventions` plugin requires the following properties to be defined in your project's `gradle.properties` file for Maven publishing:

```properties
# Project URL
project.pom.url=https://github.com/your-org/your-project

# License Information
project.pom.license.name=Your License Name
project.pom.license.url=https://your-license-url.com

# Developer Information
project.pom.developer.id=your-developer-id
project.pom.developer.name=Your Developer Name
project.pom.developer.email=developer@example.com

# SCM (Source Control Management) Information
project.pom.scm.connection=scm:git:git://github.com/your-org/your-project.git
project.pom.scm.developerConnection=scm:git:ssh://github.com/your-org/your-project.git
project.pom.scm.url=https://github.com/your-org/your-project
```

**Note:** All properties are required. If any property is missing, the build will fail with a clear error message listing all missing properties.

### Version Management

The convention plugins use lazy configuration for build tool versions, allowing consuming projects to control versions through their own `gradle.properties` file. The plugins provide sensible defaults that match the versions used in the Lockbox Framework.

#### Available Version Properties

You can override any of the following tool versions in your project's `gradle.properties`:

```properties
# Quality and Build Tools (Optional - plugins provide defaults)
lockbox.jacoco.version=0.8.11
lockbox.checkstyle.version=10.20.2
lockbox.pmd.version=7.9.0
lockbox.spotbugs.version=4.8.6
lockbox.palantir.version=2.81.0
lockbox.lombok.version=1.18.36
```

#### Default Versions

If not specified, the plugins use these defaults:

| Property | Default Version | Tool |
|----------|----------------|------|
| `lockbox.jacoco.version` | 0.8.11 | JaCoCo code coverage |
| `lockbox.checkstyle.version` | 10.20.2 | Checkstyle static analysis |
| `lockbox.pmd.version` | 7.9.0 | PMD static analysis |
| `lockbox.spotbugs.version` | 4.8.6 | SpotBugs static analysis |
| `lockbox.palantir.version` | 2.81.0 | Palantir Java Format |
| `lockbox.lombok.version` | 1.18.36 | Lombok annotations |

#### Single Source of Truth

For projects using the Lockbox Framework:
- The framework's `gradle/libs.versions.toml` is the canonical source for all versions
- Set the `lockbox.*` properties in `gradle.properties` to match your version catalog
- This ensures consistency across the framework and all consuming projects

#### Java Version

Java 21 is required for all Lockbox modules. This is hardcoded in the plugins and cannot be overridden via properties. Projects that need a different Java version should configure the toolchain directly in their build scripts.

## Building

```bash
./gradlew build
```

## Publishing

```bash
./gradlew publish
```

## License

Apache License 2.0 - See LICENSE file for details.
