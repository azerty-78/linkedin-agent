# syntax=docker/dockerfile:1
# Build context = racine du repo (linkedin-agent).
# CI : docker build -f setup/Dockerfile .

################################################################################
# Dependencies (Gradle cache)
################################################################################
FROM eclipse-temurin:21-jdk-jammy AS deps

WORKDIR /build

COPY gradlew gradlew
COPY gradle/ gradle/
COPY settings.gradle.kts build.gradle.kts ./

RUN chmod +x gradlew \
	&& ./gradlew dependencies --no-daemon --build-cache || true

################################################################################
# Package (bootJar)
################################################################################
FROM deps AS package

WORKDIR /build

COPY src/ src/

RUN ./gradlew bootJar --no-daemon -x test \
	&& JAR="$(find build/libs -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
	&& cp "$JAR" build/libs/app.jar

################################################################################
# Runtime
################################################################################
FROM eclipse-temurin:21-jre-jammy AS final

ARG UID=10001
RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/* \
	&& adduser \
	--disabled-password \
	--gecos "" \
	--home "/nonexistent" \
	--shell "/sbin/nologin" \
	--no-create-home \
	--uid "${UID}" \
	appuser

WORKDIR /app

COPY --from=package /build/build/libs/app.jar /app/app.jar

USER appuser

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
	JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
