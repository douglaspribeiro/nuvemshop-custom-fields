# Imagem em camadas para o JAR executavel do Spring Boot.
#
# O push-docker.sh roda `mvn clean package` localmente e gera
# target/nuvemshop-custom-fields-*.jar. Este Dockerfile nao recompila a app:
# apenas extrai as camadas do JAR e monta o runtime.
#
# Versoes alinhadas com o projeto:
#   Java runtime: 25, porque pom.xml compila com <release>25</release>
#   Tomcat: 10.1.41 embarcado pelo spring-boot-starter-web 3.5.0
#
# Nao usamos imagem externa de Tomcat aqui. A aplicacao Spring Boot ja inclui o
# Tomcat correto dentro do JAR; usar tomcat:11 quebraria o alinhamento de versoes
# e ainda exigiria um WAR que o Maven nao gera.

ARG SOURCE_DATE_EPOCH=1577836800

# --- Stage 1: extrai layers do JAR no host via BUILDPLATFORM -----------------
# CVE-2026-114938: binarios oficiais Corretto, fixados por SHA-256.
FROM --platform=$BUILDPLATFORM ubuntu:26.04@sha256:d66b366dac61b575bee2aef07e9c26e8070096fbe84a3d4d3eb0da069b5d76db AS java-amd64
ADD --checksum=sha256:8d2be3f035dbdb7f7475e2f7009b7dc20683bfc351ad41064218c8de596c9644 https://corretto.aws/downloads/resources/25.0.5.1.2/amazon-corretto-25.0.5.1.2-linux-x64.tar.gz /tmp/java.tar.gz
RUN mkdir /jdk && tar -xzf /tmp/java.tar.gz -C /jdk --strip-components=1 && rm /tmp/java.tar.gz

FROM --platform=$BUILDPLATFORM ubuntu:26.04@sha256:d66b366dac61b575bee2aef07e9c26e8070096fbe84a3d4d3eb0da069b5d76db AS java-arm64
ADD --checksum=sha256:9370d3e5bcf0d165e6be4a78c3f6b57160af16d3033319af583800251eb91df2 https://corretto.aws/downloads/resources/25.0.5.1.2/amazon-corretto-25.0.5.1.2-linux-aarch64.tar.gz /tmp/java.tar.gz
RUN mkdir /jdk && tar -xzf /tmp/java.tar.gz -C /jdk --strip-components=1 && rm /tmp/java.tar.gz

FROM java-${BUILDARCH} AS build-java
FROM java-${TARGETARCH} AS runtime-java

FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk@sha256:884a2f8692cc1e2c7e21f95914adf0ae0eafee9a7eb663a12169cc44468256ec AS extractor
RUN rm -rf /opt/java/openjdk
COPY --from=build-java /jdk/ /opt/java/openjdk/
ENV JAVA_VERSION=25.0.5.1.2
RUN grep -Fx 'IMPLEMENTOR_VERSION="Corretto-25.0.5.1.2"' "$JAVA_HOME/release" && java -version
ARG SOURCE_DATE_EPOCH
WORKDIR /layers
COPY docker/Healthcheck.java /tmp/Healthcheck.java
RUN javac -d /healthcheck /tmp/Healthcheck.java \
 && find /healthcheck -exec touch -h -d "@${SOURCE_DATE_EPOCH}" {} +
COPY target/*.jar /tmp/app.jar
RUN java -Djarmode=tools -jar /tmp/app.jar extract --layers --launcher --destination /layers \
 && rm /tmp/app.jar \
 && find /layers -exec touch -h -d "@${SOURCE_DATE_EPOCH}" {} +

# --- Stage 2: runtime Java 25 com Tomcat embarcado pelo Spring Boot ----------
FROM eclipse-temurin:25-jre@sha256:945247f9d092703e0c6be92243ef3c51640229e23ab34d353c8dcdc73e6cd4e6
RUN rm -rf /opt/java/openjdk
COPY --from=runtime-java /jdk/ /opt/java/openjdk/
ENV JAVA_VERSION=25.0.5.1.2
RUN grep -Fx 'IMPLEMENTOR_VERSION="Corretto-25.0.5.1.2"' "$JAVA_HOME/release" && java -version
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ARG APP_VERSION=unknown
ENV APP_VERSION=${APP_VERSION}
# Infra refuses to enable B on images without scheduler isolation.
LABEL app.background-control="true" app.deploy.protocol="1"
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS=""

WORKDIR /app

# Camadas estaveis primeiro para maximizar deduplicacao no registry.
COPY --from=extractor /healthcheck/ /app/healthcheck/
COPY --from=extractor /layers/dependencies/ ./
COPY --from=extractor /layers/spring-boot-loader/ ./
COPY --from=extractor /layers/snapshot-dependencies/ ./
COPY --from=extractor /layers/application/ ./

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
