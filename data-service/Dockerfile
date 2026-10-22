FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY docker/flink/pom.xml ./flink-deps/pom.xml
RUN mvn -q -DskipTests dependency:go-offline \
    && mvn -q -f flink-deps/pom.xml dependency:copy-dependencies -DoutputDirectory=/workspace/flink-libs
COPY src ./src
RUN mvn -q -DskipTests package \
    && mvn -q -DskipTests dependency:copy-dependencies \
        -DincludeScope=runtime \
        -DoutputDirectory=/workspace/app-libs

FROM flink:1.20.3-scala_2.12-java17

ARG FLINK_CDC_VERSION=3.7.0-1.20
ARG MYSQL_DRIVER_VERSION=8.4.0
ARG FLINK_CDC_TARBALL_URL=https://archive.apache.org/dist/flink/flink-cdc-3.7.0/flink-cdc-3.7.0-1.20-bin.tar.gz

WORKDIR /app

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL "${FLINK_CDC_TARBALL_URL}" \
        | tar -xz -C /opt \
    && mv "/opt/flink-cdc-${FLINK_CDC_VERSION}" /opt/flink-cdc \
    && curl -fsSL -o "/opt/flink-cdc/lib/flink-cdc-pipeline-connector-mysql-${FLINK_CDC_VERSION}.jar" \
        "https://repo1.maven.org/maven2/org/apache/flink/flink-cdc-pipeline-connector-mysql/${FLINK_CDC_VERSION}/flink-cdc-pipeline-connector-mysql-${FLINK_CDC_VERSION}.jar" \
    && curl -fsSL -o "/opt/flink-cdc/lib/flink-cdc-pipeline-connector-iceberg-${FLINK_CDC_VERSION}.jar" \
        "https://repo1.maven.org/maven2/org/apache/flink/flink-cdc-pipeline-connector-iceberg/${FLINK_CDC_VERSION}/flink-cdc-pipeline-connector-iceberg-${FLINK_CDC_VERSION}.jar" \
    && curl -fsSL -o "/opt/flink-cdc/lib/mysql-connector-j-${MYSQL_DRIVER_VERSION}.jar" \
        "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${MYSQL_DRIVER_VERSION}/mysql-connector-j-${MYSQL_DRIVER_VERSION}.jar"

COPY --from=build /workspace/flink-libs/*.jar /opt/flink/lib/
RUN cp /opt/flink-cdc/lib/mysql-connector-j-*.jar /opt/flink/lib/
RUN rm -f /opt/flink/lib/commons-math3-*.jar
COPY --from=build /workspace/target/data-service-0.1.0-SNAPSHOT.jar.original app.jar
COPY --from=build /workspace/app-libs /app/lib
COPY docker/entrypoint-data-service.sh /entrypoint-data-service.sh
RUN chmod 0755 /entrypoint-data-service.sh

ENV FLINK_HOME=/opt/flink \
    FLINK_CDC_HOME=/opt/flink-cdc

EXPOSE 8081
ENTRYPOINT ["/entrypoint-data-service.sh"]
