# Java Conventions Plugin

## Overview

The **Lockbox Java Conventions Plugin** (`com.lockbox.gradle.plugins.java-conventions`) is a comprehensive Gradle convention plugin that establishes standardized configurations for all Java modules in the Lockbox ecosystem. It centralizes build configurations, quality tools, testing frameworks, and publishing settings to ensure consistency across all Java projects.

## Table of Contents

- [Quick Start](#quick-start)
- [Features](#features)
- [Plugin Configuration](#plugin-configuration)
  - [Version Constants](#version-constants)
  - [Java Toolchain](#java-toolchain)
  - [Dependency Management](#dependency-management)
- [Testing](#testing)
  - [Unit Tests](#unit-tests)
  - [Integration Tests](#integration-tests)
  - [Code Coverage](#code-coverage)
- [Code Quality Tools](#code-quality-tools)
  - [Spotless](#spotless)
  - [Checkstyle](#checkstyle)
  - [PMD](#pmd)
  - [SpotBugs](#spotbugs)
- [Documentation](#documentation)
  - [Javadoc Generation](#javadoc-generation)
- [Maven Publishing](#maven-publishing)
  - [Required Properties](#required-properties)
  - [Publishing to CodeArtifact](#publishing-to-codeartifact)
  - [Publishing to Maven Local](#publishing-to-maven-local)
- [Reproducible Builds](#reproducible-builds)
- [Available Tasks](#available-tasks)
- [Troubleshooting](#troubleshooting)

---

## Quick Start

### Applying the Plugin

In your module's `build.gradle.kts`:

```kotlin
plugins {
    id("com.lockbox.gradle.plugins.java-conventions")
}
```

That's it! The plugin automatically configures:
- Java 21 toolchain
- Testing frameworks (JUnit Jupiter)
- Code quality tools (Checkstyle, PMD, SpotBugs, Spotless)
- Code coverage (JaCoCo)
- Documentation generation (Javadoc)
- Maven publishing
- Reproducible builds

---

## Features

### Core Capabilities

- **Java 21 Toolchain**: Standardized Java version across all modules
- **Dependency Locking**: Reproducible builds with locked dependency versions
- **Quality Tools**: Pre-configured Checkstyle, PMD, SpotBugs, and Spotless
- **Testing**: JUnit Jupiter with parallel execution and integration test suite
- **Code Coverage**: JaCoCo with configurable thresholds
- **Documentation**: Intelligent Javadoc generation with external links
- **Publishing**: Maven-compatible publishing to AWS CodeArtifact or Maven Local
- **Test Fixtures**: Built-in support for shared test utilities

---

## Plugin Configuration

### Version Constants

The plugin maintains centralized version constants (must match `framework-platform.gradle.kts`):

| Constant | Version | Description |
|----------|---------|-------------|
| `JAVA` | 21 | Java language version |
| `JACOCO` | 0.8.11 | Code coverage tool |
| `CHECKSTYLE` | 10.20.2 | Style checking |
| `PMD` | 7.9.0 | Source code analyzer |
| `SPOTBUGS` | 4.8.6 | Bug detection |
| `PALANTIR_JAVA_FORMAT` | 2.81.0 | Code formatter |
| `LOMBOK` | 1.18.36 | Boilerplate reducer |

### Java Toolchain

The plugin configures Java 21 with:

```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    
    withJavadocJar()
    withSourcesJar()
}
```

**Compilation flags:**
- `-parameters`: Preserves parameter names for reflection
- `release = 21`: Ensures bytecode compatibility

### Dependency Management

#### Dependency Locking

All configurations are locked for reproducible builds:

```kotlin
dependencyLocking {
    lockAllConfigurations()
}
```

**Commands:**
```bash
# Generate/update lock files
./gradlew dependencies --write-locks

# Verify locked dependencies
./gradlew buildEnvironment --write-locks
```

#### Resolution Strategy

The plugin forces specific dependency versions to avoid conflicts:

```kotlin
configurations.all {
    resolutionStrategy {
        force("org.yaml:snakeyaml:2.4")
    }
}
```

This prevents Android variant resolution issues with SnakeYAML.

---

## Testing

### Unit Tests

Unit tests use **JUnit Jupiter** with parallel execution enabled for faster test runs.

**Configuration:**
- Test framework: JUnit Jupiter (JUnit 5)
- Parallel execution: Enabled (dynamic strategy)
- Max parallel forks: Half of available CPU cores
- JVM crash dumps: Enabled on OutOfMemoryError

**Running unit tests:**
```bash
./gradlew test
```

**Parallel Execution Settings:**
```kotlin
systemProperty("junit.jupiter.execution.parallel.enabled", "true")
systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
```

**Disable parallel execution for specific tests:**
```java
@Execution(ExecutionMode.SAME_THREAD)
class SequentialTest {
    // Tests that must run sequentially
}
```

### Integration Tests

The plugin creates a separate **integrationTest** source set and test suite.

**Directory structure:**
```
src/
  main/java/
  test/java/              # Unit tests
  integrationTest/java/   # Integration tests
```

**Running integration tests:**
```bash
./gradlew integrationTest
./gradlew testAll  # Runs both unit and integration tests
```

**Key differences from unit tests:**
- Sequential execution (no parallel forks)
- JUnit parallel execution disabled
- Runs after unit tests
- Separate test reports
- Profile: `spring.profiles.active=integration-test`

**Integration test dependencies:**

Integration tests automatically have access to main sources. Add additional dependencies in your module's build file:

```kotlin
dependencies {
    integrationTestImplementation("org.testcontainers:testcontainers:1.19.0")
    integrationTestImplementation("org.testcontainers:postgresql:1.19.0")
}
```

**Why sequential execution?**

Integration tests often use Testcontainers or shared resources (databases, message queues) that don't handle parallel access well. Sequential execution prevents:
- Port conflicts
- Container naming conflicts
- Database state corruption
- Resource exhaustion

### Code Coverage

**JaCoCo** tracks code coverage for unit tests.

**Running coverage:**
```bash
./gradlew test          # Automatically generates report
./gradlew jacocoTestReport
./gradlew jacocoCheck   # Verifies minimum coverage threshold
```

**Coverage reports:**
- XML: `build/reports/jacoco/test/jacocoTestReport.xml`
- HTML: `build/reports/jacoco/test/html/index.html`

**Default threshold:**
- Minimum line coverage: 1% (BUNDLE level)

**Customizing thresholds:**

Override in your module's `build.gradle.kts`:

```kotlin
tasks.named<JacocoCoverageVerification>("jacocoCheck") {
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()  // 80% coverage
            }
        }
        rule {
            element = "CLASS"
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()  // 70% branch coverage
            }
        }
    }
}
```

---

## Code Quality Tools

The plugin includes four quality tools with pre-configured rulesets.

### Spotless

**Spotless** enforces code formatting using Palantir Java Format.

**What it does:**
- Applies consistent code formatting
- Adds Apache 2.0 license headers
- Trims trailing whitespace
- Ensures files end with newline
- Supports toggle comments (`// spotless:off` / `// spotless:on`)

**Running Spotless:**
```bash
./gradlew spotlessCheck   # Check formatting
./gradlew spotlessApply   # Apply formatting
```

**Integration:**
- Runs automatically before compilation (`compileJava`, `compileTestJava`)
- Checked during `./gradlew check`

**Toggle formatting:**
```java
// spotless:off
public class UnformattedCode {
    // This code won't be reformatted
}
// spotless:on
```

### Checkstyle

**Checkstyle** enforces coding standards and style guidelines.

**Configuration:**
- Config file: Extracted from plugin resources
- Max warnings: 0
- Max errors: 0
- Fail on violations: Yes

**Running Checkstyle:**
```bash
./gradlew checkstyleMain
./gradlew checkstyleTest
```

**Reports:**
- XML: `build/reports/checkstyle/main.xml`
- HTML: `build/reports/checkstyle/main.html`

**Customizing rules:**

The plugin uses embedded configuration. To override specific rules in your module:

```kotlin
tasks.withType<Checkstyle>().configureEach {
    configProperties["checkstyle.suppression.filter"] = "custom-suppressions.xml"
}
```

### PMD

**PMD** performs static source code analysis to find common programming flaws.

**What it detects:**
- Unused variables
- Empty catch blocks
- Unnecessary object creation
- Complex expressions
- Duplicate code

**Running PMD:**
```bash
./gradlew pmdMain
./gradlew pmdTest
```

**Reports:**
- XML: `build/reports/pmd/main.xml`
- HTML: `build/reports/pmd/main.html`

**Configuration:**
- Ruleset: Extracted from plugin resources
- Console output: Enabled
- Fail on violations: Yes

### SpotBugs

**SpotBugs** detects potential bugs in Java bytecode.

**Configuration:**
- Effort: MAX
- Report level: LOW (most comprehensive)
- Exclude filter: Custom exclusions for known false positives

**Running SpotBugs:**
```bash
./gradlew spotbugsMain
./gradlew spotbugsTest
```

**Reports:**
- XML: `build/reports/spotbugs/main.xml`
- HTML: `build/reports/spotbugs/main.html` (fancy-hist style)

**Suppressing false positives:**

In your code:
```java
@SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH", 
                    justification = "Validated by framework")
public void processData(String data) {
    // Implementation
}
```

### Running All Quality Tools

**Combined lint task:**
```bash
./gradlew lint
```

This runs:
- `checkstyleMain`
- `checkstyleTest`
- `pmdMain`
- `pmdTest`
- `spotbugsMain`
- `spotbugsTest`

**Automatic execution:**

Quality tools run automatically with:
```bash
./gradlew check  # Runs lint + tests
./gradlew build  # Runs check + assembles artifacts
```

---

## Documentation

### Javadoc Generation

The plugin configures intelligent Javadoc generation with:
- External links to Java SE and Lombok APIs
- HTML5 output format
- Private member documentation
- Automatic skip for packages without public classes
- Reproducible timestamps disabled

**Generating Javadocs:**
```bash
./gradlew javadoc
```

**Output:** `build/docs/javadoc/index.html`

**Automatic artifacts:**
- Javadoc JAR: Automatically created for publishing
- Sources JAR: Automatically created for publishing

**Smart skip logic:**

The plugin automatically skips Javadoc generation if:
- No Java source files exist
- No public or protected classes found
- Only package-info files exist

This prevents the "No public or protected classes found to document" error.

**External links:**
```kotlin
links("https://docs.oracle.com/en/java/javase/21/docs/api/")
links("https://javadoc.io/doc/org.projectlombok/lombok/1.18.36/")
```

**Customizing Javadoc:**

In your module's `build.gradle.kts`:

```kotlin
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        links("https://docs.spring.io/spring-framework/docs/current/javadoc-api/")
        overview = "src/main/javadoc/overview.html"
    }
}
```

---

## Maven Publishing

The plugin configures Maven-compatible publishing to AWS CodeArtifact or Maven Local.

### Required Properties

Add these to your **project's** `gradle.properties`:

```properties
# Project metadata
project.pom.url=https://github.com/your-org/your-repo
project.pom.license.name=The Apache License, Version 2.0
project.pom.license.url=http://www.apache.org/licenses/LICENSE-2.0.txt

# Developer information
project.pom.developer.id=yourId
project.pom.developer.name=Your Name
project.pom.developer.email=your.email@example.com

# SCM information
project.pom.scm.connection=scm:git:git://github.com/your-org/your-repo.git
project.pom.scm.developerConnection=scm:git:ssh://github.com/your-org/your-repo.git
project.pom.scm.url=https://github.com/your-org/your-repo
```

**Missing properties?**

The plugin will throw a detailed error listing all missing properties.

### Publishing to CodeArtifact

**Prerequisites:**
1. AWS credentials configured
2. CodeArtifact repository created
3. Environment variables or project properties set

**Environment variables:**
```bash
export CODEARTIFACT_DOMAIN=your-domain
export CODEARTIFACT_ACCOUNT_ID=123456789012
export AWS_REGION=us-east-1
export CODEARTIFACT_AUTH_TOKEN=$(aws codeartifact get-authorization-token \
    --domain your-domain \
    --query authorizationToken \
    --output text)
```

**Or project properties:**
```properties
codeartifact.domain=your-domain
codeartifact.accountId=123456789012
aws.region=us-east-1
codeartifact.authToken=<token>
```

**Repository routing:**
- **SNAPSHOT versions** → `snapshots` repository (or `JAVA_SNAPSHOTS_REPO_NAME`)
- **Release versions** → `releases` repository (or `JAVA_REPO_NAME`)

**Publishing:**
```bash
# Publish to CodeArtifact
./gradlew publish

# Publish specific publication
./gradlew publishMavenJavaPublicationToCodeArtifactRepository
```

### Publishing to Maven Local

**For local development and testing:**

```bash
export PUBLISH_TO_MAVEN_LOCAL=true
./gradlew publishToMavenLocal
```

This publishes to `~/.m2/repository/` without requiring CodeArtifact credentials.

**Publication details:**
- Publication name: `mavenJava`
- Artifact ID: Defaults to project name
- Includes: Main JAR, sources JAR, Javadoc JAR
- POM: Complete with dependencies, licenses, developers, SCM

**Test fixtures:**

Test fixtures are published as separate artifacts:
- `your-module-test-fixtures.jar`

POM metadata warnings for `testFixturesApiElements` and `testFixturesRuntimeElements` are suppressed (expected behavior).

---

## Reproducible Builds

The plugin ensures reproducible builds through:

### 1. Dependency Locking
```kotlin
dependencyLocking {
    lockAllConfigurations()
}
```

### 2. Deterministic Archive Properties
```kotlin
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
```

### 3. Deterministic Javadoc
```kotlin
addBooleanOption("notimestamp", true)
```

**Benefits:**
- Same inputs → same outputs (byte-for-byte identical)
- Verifiable builds
- Enhanced security
- Reliable caching

**Verify reproducibility:**
```bash
./gradlew clean build
sha256sum build/libs/*.jar

./gradlew clean build
sha256sum build/libs/*.jar
# Checksums should match
```

---

## Available Tasks

### Build Tasks
- `build` - Assembles and tests this project
- `clean` - Deletes the build directory
- `assemble` - Assembles the outputs of this project

### Testing Tasks
- `test` - Runs unit tests
- `integrationTest` - Runs integration tests
- `testAll` - Runs all unit and integration tests
- `check` - Runs all checks (tests + quality tools)

### Quality Tasks
- `spotlessCheck` - Checks code formatting
- `spotlessApply` - Applies code formatting
- `checkstyleMain` - Runs Checkstyle on main sources
- `checkstyleTest` - Runs Checkstyle on test sources
- `pmdMain` - Runs PMD on main sources
- `pmdTest` - Runs PMD on test sources
- `spotbugsMain` - Runs SpotBugs on main classes
- `spotbugsTest` - Runs SpotBugs on test classes
- `lint` - Runs all quality tools

### Coverage Tasks
- `jacocoTestReport` - Generates code coverage report
- `jacocoCheck` - Verifies code coverage meets minimum threshold

### Documentation Tasks
- `javadoc` - Generates Javadoc API documentation

### Publishing Tasks
- `publish` - Publishes all publications
- `publishToMavenLocal` - Publishes to local Maven repository (~/.m2)
- `publishMavenJavaPublicationToCodeArtifactRepository` - Publishes to CodeArtifact

### Dependency Tasks
- `dependencies` - Displays all dependencies
- `dependencyInsight` - Displays insight into a specific dependency
- `dependencies --write-locks` - Generates/updates dependency lock files

### Internal Tasks
- `extractQualityToolConfigs` - Extracts configuration files from plugin resources

---

## Troubleshooting

### Issue: Dependency locking failures

**Symptom:**
```
Dependency lock state is out of date
```

**Solution:**
```bash
./gradlew dependencies --write-locks
```

### Issue: Quality tool violations

**Symptom:**
```
Checkstyle rule violations found
```

**Solution:**
1. Review report: `build/reports/checkstyle/main.html`
2. Fix violations manually or run `./gradlew spotlessApply` for formatting issues
3. For false positives, configure suppressions in your module

### Issue: Integration tests fail with Testcontainers

**Symptom:**
```
Port 5432 already in use
```

**Solution:**

Integration tests run sequentially by default. If you've overridden this:

```kotlin
// DON'T do this for integration tests:
// maxParallelForks = 4

// Keep sequential:
maxParallelForks = 1
```

### Issue: Javadoc generation fails

**Symptom:**
```
No public or protected classes found to document
```

**Solution:**

The plugin automatically skips Javadoc when no public classes exist. If you're seeing this error, you may have overridden the plugin's configuration. Remove custom Javadoc configurations.

### Issue: Publishing fails with "Required property not found"

**Symptom:**
```
Required property 'project.pom.url' not found in gradle.properties
```

**Solution:**

Add all required properties to `gradle.properties`. See [Required Properties](#required-properties).

### Issue: CodeArtifact authentication fails

**Symptom:**
```
401 Unauthorized
```

**Solution:**
```bash
# Refresh CodeArtifact token (expires after 12 hours)
export CODEARTIFACT_AUTH_TOKEN=$(aws codeartifact get-authorization-token \
    --domain your-domain \
    --query authorizationToken \
    --output text)

# Verify token is set
echo $CODEARTIFACT_AUTH_TOKEN
```

### Issue: SnakeYAML Android variant conflict

**Symptom:**
```
Could not resolve org.yaml:snakeyaml:2.4:android
```

**Solution:**

The plugin automatically forces the standard JAR variant. If you're still seeing this error, ensure you haven't overridden the resolution strategy:

```kotlin
// This is handled by the plugin - don't override
configurations.all {
    resolutionStrategy {
        force("org.yaml:snakeyaml:2.4")
    }
}
```

### Issue: Parallel test execution causes failures

**Symptom:**
```
Tests fail intermittently or only when run in parallel
```

**Solution:**

Disable parallel execution for problematic test classes:

```java
@Execution(ExecutionMode.SAME_THREAD)
class DatabaseTest {
    // Tests that share state
}
```

Or disable globally in your module:

```kotlin
tasks.withType<Test> {
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
```

---

## Best Practices

### 1. Don't Override Plugin Configurations

The plugin provides battle-tested configurations. Avoid overriding unless absolutely necessary.

❌ **Don't:**
```kotlin
java {
    sourceCompatibility = JavaVersion.VERSION_17  // Conflicts with plugin
}
```

✅ **Do:**
```kotlin
// Let the plugin handle Java configuration
plugins {
    id("com.lockbox.gradle.plugins.java-conventions")
}
```

### 2. Use Integration Tests for External Dependencies

❌ **Don't:**
```java
// In src/test/java - unit test should be fast and isolated
@Test
void testDatabaseIntegration() {
    // Connects to real database
}
```

✅ **Do:**
```java
// In src/integrationTest/java
@Test
void testDatabaseIntegration() {
    // Integration test with Testcontainers
}
```

### 3. Keep Lock Files in Version Control

```bash
git add **/gradle.lockfile
git commit -m "Update dependency locks"
```

### 4. Run Quality Checks Locally

Before pushing:
```bash
./gradlew clean build
```

This runs:
- Compilation
- Spotless formatting
- All quality tools (Checkstyle, PMD, SpotBugs)
- Unit tests
- Integration tests
- Code coverage

### 5. Use Test Fixtures for Shared Test Code

Create reusable test utilities:

```
src/
  testFixtures/java/
    com/yourpackage/test/
      TestDataBuilder.java
      MockFactory.java
```

```kotlin
dependencies {
    testFixturesApi("org.junit.jupiter:junit-jupiter-api")
    testFixturesImplementation("org.mockito:mockito-core")
}
```

Other modules can then use:
```kotlin
dependencies {
    testImplementation(testFixtures(project(":your-module")))
}
```

---

## Additional Resources

- [Gradle Documentation](https://docs.gradle.org/)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Checkstyle Checks](https://checkstyle.sourceforge.io/checks.html)
- [PMD Rules](https://pmd.github.io/latest/pmd_rules_java.html)
- [SpotBugs Bug Descriptions](https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html)
- [Palantir Java Format](https://github.com/palantir/palantir-java-format)

---

## License

This plugin is part of the Lockbox project and is licensed under the Apache License 2.0.

