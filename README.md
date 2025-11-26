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

### Publishing to Maven Local

For local development and testing:

```bash
./gradlew publishToMavenLocal
```

Or using the publish script:

```bash
PUBLISH_TO_MAVEN_LOCAL=true ./publish.sh
```

### Publishing to AWS CodeArtifact

For publishing to your organization's private AWS CodeArtifact repository:

#### Prerequisites

- AWS CLI installed and configured with appropriate credentials
- Access to the AWS CodeArtifact domain and repository
- `.env` file configured with your CodeArtifact settings

#### Setup

1. **Copy the environment template**:

```bash
cp .env.template .env
```

2. **Edit `.env` with your AWS CodeArtifact configuration**:

```bash
CODEARTIFACT_DOMAIN=your-domain
CODEARTIFACT_ACCOUNT_ID=123456789012
CODEARTIFACT_REGION=us-east-1
CODEARTIFACT_JAVA_REPOSITORY=your-releases-repo
```

**Security Note:** The `.env` file is git-ignored and will never be committed to version control. It contains your organization's AWS account information.

#### Publishing

Simply run the publish script:

```bash
./publish.sh
```

The script will:
1. Load configuration from `.env`
2. Obtain a CodeArtifact authorization token using AWS CLI
3. Build the plugins
4. Publish to your CodeArtifact releases repository

#### What Gets Published

The script publishes all 7 plugins to CodeArtifact:
- `io.github.lockboxai:lockbox-gradle-plugins:VERSION` (the plugin JAR)
- All plugin metadata and marker artifacts
- Sources and Javadoc JARs

#### Troubleshooting

If publishing fails:

1. **Check AWS credentials**: Ensure your AWS CLI is configured correctly
   ```bash
   aws sts get-caller-identity
   ```

2. **Verify CodeArtifact access**: Test that you can access the domain
   ```bash
   aws codeartifact list-repositories --domain your-domain --region your-region
   ```

3. **Check `.env` file**: Ensure all required variables are set correctly

4. **Review build logs**: The script provides detailed output for debugging

### Publishing to Gradle Plugin Portal

#### Prerequisites

Before publishing to the Gradle Plugin Portal, you need to:

1. **Create a Gradle Plugin Portal account** at https://plugins.gradle.org/user/register
2. **Retrieve your API key** from the "API Keys" tab in your profile
3. **Configure credentials** in `~/.gradle/gradle.properties`:

```properties
gradle.publish.key=<your-key>
gradle.publish.secret=<your-secret>
```

**Security Note:** Never commit these credentials to version control. The `~/.gradle/gradle.properties` file in your home directory is the recommended location.

#### Validation

Before publishing, validate your plugin configuration without uploading:

```bash
./gradlew publishPlugins --validate-only
```

This command checks:
- All required plugin metadata is present
- Plugin artifacts can be generated correctly
- No configuration errors exist

#### Publishing

To publish all plugins to the Gradle Plugin Portal:

```bash
./gradlew publishPlugins
```

You can also pass credentials on the command line (useful for CI/CD):

```bash
./gradlew publishPlugins -Pgradle.publish.key=<key> -Pgradle.publish.secret=<secret>
```

#### After Publishing

- Your plugins will go through an **approval process** which typically takes 1-3 business days
- You'll receive email notifications about the approval status
- Once approved, plugins will be immediately available on the [Gradle Plugin Portal](https://plugins.gradle.org/)
- Users can then apply your plugins using the standard `plugins {}` DSL

#### Published Plugins

This project publishes 7 plugins:

**AWS Integration:**
- `io.github.lockboxai.aws-environment` - AWS authentication and environment setup
- `io.github.lockboxai.codeartifact-repositories` - CodeArtifact Maven repositories
- `io.github.lockboxai.codeartifact-repositories-settings` - CodeArtifact plugin repositories

**Convention Plugins:**
- `io.github.lockboxai.java-conventions` - Base Java configuration with quality tools
- `io.github.lockboxai.module-conventions` - Lockbox module configuration
- `io.github.lockboxai.spring-boot-conventions` - Spring Boot application configuration
- `io.github.lockboxai.spring-shell-conventions` - Spring Shell CLI configuration

## License

Apache License 2.0 - See LICENSE file for details.
