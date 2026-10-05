FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw dependency:go-offline -B -q
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw verify -B -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
    && mkdir -p /app/logs /app/uploads && chown -R appuser:appgroup /app
COPY --from=builder --chown=appuser:appgroup /app/target/ecom_be-*.jar /app/app.jar
USER appuser
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
