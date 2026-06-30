# Multi-stage build for the Spring Boot 4 / Java 25 backend.
# Stage 1 builds the bootJar with the project's pinned Gradle (via the wrapper).
# Stage 2 extracts Spring Boot layers so dependency layers stay cached across
# code-only rebuilds. Stage 3 is a slim JRE runtime running as a non-root user.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
# Copy build scripts + wrapper first so the dependency download layer is cached
# until the build config actually changes.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew
COPY src ./src
# Tests run in CI, not in the image build.
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre AS layers
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd -r -u 1001 spring
COPY --from=layers /app/extracted/dependencies/ ./
COPY --from=layers /app/extracted/spring-boot-loader/ ./
COPY --from=layers /app/extracted/snapshot-dependencies/ ./
COPY --from=layers /app/extracted/application/ ./
USER spring
EXPOSE 8082
# Spring Boot 4 `tools` jarmode extracts a thin launcher (app.jar) + lib/ — run it
# with `java -jar`, NOT the old layertools JarLauncher class.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
