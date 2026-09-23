# Gradle Wrapper

The wrapper is generated and committed as of Phase 00:

- `gradlew`, `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.14)

Use `./gradlew` for every build so that all machines and CI use the same Gradle version.
Do not invoke a locally installed `gradle` binary.

To upgrade Gradle later:

```bash
./gradlew wrapper --gradle-version <version> --distribution-type bin
```
