# syntax=docker/dockerfile:1
# Nova versão do ReMoto: imagem do remoto-web. Os dados ficam no volume /data.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY remoto-core/pom.xml remoto-core/
COPY remoto-storage/pom.xml remoto-storage/
COPY remoto-web/pom.xml remoto-web/
COPY remoto-cli/pom.xml remoto-cli/
RUN ./mvnw -B -q -pl remoto-web -am dependency:go-offline || true
COPY remoto-core/src remoto-core/src
COPY remoto-storage/src remoto-storage/src
COPY remoto-web/src remoto-web/src
RUN ./mvnw -B -q -pl remoto-web -am package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 remoto && mkdir /data && chown remoto /data
WORKDIR /app
COPY --from=build /src/remoto-web/target/remoto-web-*.jar /app/remoto.jar
COPY scenarios/ /app/scenarios/
ENV REMOTO_DATA_DIR=/data \
    REMOTO_PUBLIC_SCENARIOS_DIR=/app/scenarios
USER remoto
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/remoto.jar"]
