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
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS extractor
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
FROM eclipse-temurin:25-jre

ARG APP_VERSION=unknown
ENV APP_VERSION=${APP_VERSION}
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
