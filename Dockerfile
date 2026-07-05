# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

ENV SERVER_PORT=19090 \
    RUNNING_IN_DOCKER=true \
    STOCKPREDICTOR_DATA_DIR=/data \
    STOCKPREDICTOR_JDBC_URL=jdbc:h2:file:/data/stockdb;DB_CLOSE_ON_EXIT=FALSE;AUTO_RECONNECT=TRUE \
    APP_BROWSER_AUTO_OPEN=false \
    JAVA_TOOL_OPTIONS=-Djava.awt.headless=true

RUN mkdir -p /data

COPY --from=build /build/target/*.jar /app/app.jar
COPY packaging-assets/stockdb.mv.db /opt/stockpredictor-seed/stockdb.mv.db
COPY packaging-assets/docker-entrypoint.sh /usr/local/bin/stockpredictor-entrypoint

RUN chmod +x /usr/local/bin/stockpredictor-entrypoint

EXPOSE 19090
VOLUME ["/data"]

ENTRYPOINT ["/usr/local/bin/stockpredictor-entrypoint"]
