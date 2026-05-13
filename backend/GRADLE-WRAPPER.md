# Gradle wrapper

`./gradlew` is missing from this scaffold to keep the archive small.
Generate it once locally with:

    cd backend
    gradle wrapper --gradle-version 8.10

after which `./gradlew bootRun`, `./gradlew test`, etc. all work without a system Gradle.
