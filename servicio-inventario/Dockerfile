# Inventory service runtime image.
#
# The reactor parent POM lives outside this build context, so the executable jar is produced by
# Maven before the image is built:
#     mvn -pl servicio-inventario -am -DskipTests package
#     docker build -t servicio-inventario:local servicio-inventario/
#
# Stage 1 validates that the artifact really is an executable Spring Boot jar, keeping the JDK out
# of the runtime image. Stage 2 ships only the JRE plus the jar, running as a non-root user.
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build
COPY target/*.jar app.jar

RUN jar tf app.jar | grep -q '^BOOT-INF/classes/application.yml$' \
    && jar tf app.jar | grep -q '^BOOT-INF/classes/com/example/microservicios/inventario/InventarioApplication.class$'

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S app && adduser -S -G app -u 10001 -h /app app

WORKDIR /app
COPY --from=builder --chown=app:app /build/app.jar /app/app.jar

USER app

EXPOSE 8081

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"

HEALTHCHECK --interval=5s --timeout=3s --start-period=40s --retries=6 \
    CMD wget -q -O /dev/null http://127.0.0.1:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
