# AGENTS.md

## Cursor Cloud specific instructions

### Repository overview

**NoticiasPlaneta** is a news portal project (Java). The repository is currently a starter/skeleton with only a README, LICENSE, and `.gitignore`. No source code or build system has been added yet.

### Environment

- **Java**: OpenJDK 21 (system-installed at `/usr/lib/jvm/java-21-openjdk-amd64`)
- **Build tool**: Apache Maven 3.8.7 (installed via `apt`)
- **No build files exist yet** (`pom.xml`, `build.gradle`, etc.) — the first contributor will need to initialize a Maven or Gradle project structure.

### Development notes

- The `.gitignore` is Java-oriented (ignores `.class`, `.jar`, `.war`, etc.).
- When a `pom.xml` or `build.gradle` is added, use standard commands: `mvn compile`, `mvn test`, `mvn exec:java`, or the Gradle equivalents.
- No lint, test, or build commands are available until a build file is added to the project.
