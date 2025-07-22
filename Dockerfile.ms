# Stage 1: Desarrollo, construir la aplicacion.
FROM maven:3.9.5-eclipse-temurin-17-alpine AS builder

WORKDIR /app

COPY pom.xml .

COPY src ./src

RUN mvn clean package -DskipTests

# Stage 2: Crear la imagen de producción.
FROM open-liberty:kernel-slim-java17-openj9 AS prod

USER root

RUN groupadd executer && \
    useradd --no-log-init -g executer -d /home/executer -s /sbin/nologin executer && \
    mkdir -p /home/executer && \
    chown -R executer:executer /opt/ol /home/executer /logs

USER executer

RUN featureUtility installFeature \
    jakartaee-10.0 \
    microprofile-7.0 \
    passwordUtilities-1.1 \
    ssl-1.0

COPY liberty-config/server.xml /opt/ol/wlp/usr/servers/defaultServer/

COPY --from=builder /app/target/openliberty-mongo-app.war /opt/ol/wlp/usr/servers/defaultServer/apps/

COPY --chown=executer:executer certs/truststore.p12 /opt/ol/wlp/output/defaultServer/resources/security/truststore.p12

EXPOSE 9080

CMD ["server", "run", "defaultServer"]
