# ── Stage 1: Build ────────────────────────────────────────────────────────────
# Use the full JDK image only for the build stage.
# This image never ships to production — it's discarded after the build.
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy the Maven wrapper and POM first — Docker caches this layer separately.
# Dependencies are only re-downloaded when pom.xml changes, not on every code change.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Now copy source and build. This layer is cache-busted on any code change.
COPY src ./src
RUN ./mvnw package -DskipTests -q

# Extract Spring Boot layers for optimal Docker layer caching.
# On re-deploy, only changed layers (usually just 'application') are pushed.
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
# JRE-only image — no compiler, no build tools in production.
# Alpine base keeps the image small (~80MB vs ~300MB for full JDK).
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as a non-root user.
# Never run a production container as root.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy Spring Boot layers in order of least to most likely to change.
# This maximises Docker layer cache hits on re-deploy.
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/dependencies ./
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/spring-boot-loader ./
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/snapshot-dependencies ./
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/application ./

# Expose the port the app listens on.
EXPOSE 8080

# Health check so Docker/Kubernetes knows when the container is ready.
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers:
# -XX:+UseContainerSupport  → respect container CPU/memory limits (not host)
# -XX:MaxRAMPercentage=75   → use up to 75% of container memory for heap
# -XX:+ExitOnOutOfMemoryError → crash cleanly on OOM so K8s can restart the pod
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
