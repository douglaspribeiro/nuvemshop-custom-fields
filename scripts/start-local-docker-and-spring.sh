#!/usr/bin/env bash

set -euo pipefail

LOCAL_PORT="3307"
LOCAL_DB="nuvem_custom_fields"
LOCAL_ROOT_PASS="root"
LOCAL_APP_USER="nuvem_custom_fields"
LOCAL_APP_PASS="nuvem_custom_fields"
LOCAL_TZ="America/Sao_Paulo"
SPRING_PROFILE="docker"
LOCAL_APP_BASE_URL="${APP_BASE_URL:-https://chlorine-mutate-preface.ngrok-free.dev}"
LOCAL_REDIRECT_URI="${NUVEMSHOP_REDIRECT_URI:-}"
DOCKER_ONLY="false"
RESET_DB="false"
APP_DIR="."
COMPOSE_FILE="docker-compose.mysql-local.yml"

usage() {
  cat <<'EOF'
Uso:
  ./scripts/start-local-docker-and-spring.sh [opcoes] [-- args-do-maven]

Opcoes:
  --local-port PORT           Porta local do MySQL compartilhado (padrao: 3307)
  --local-db DATABASE         Database do app dentro do mysql-local
  --local-root-pass PASSWORD  Senha root do mysql-local (padrao: root)
  --local-app-user USER       Usuario local da aplicacao
  --local-app-pass PASSWORD   Senha local da aplicacao
  --local-tz TZ               Timezone do container (padrao: America/Sao_Paulo)
  --spring-profile PROFILE    Profile Spring (padrao: docker)
  --app-base-url URL          URL publica do app (padrao: tunnel ngrok local)
  --docker-only               Sobe/prepara apenas o MySQL
  --reset-db                  Recria o volume compartilhado mysql-local-data
  -h, --help                  Mostra esta ajuda

Exemplo:
  ./scripts/start-local-docker-and-spring.sh -- -DskipTests
EOF
}

MVN_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --local-port) LOCAL_PORT="$2"; shift 2 ;;
    --local-db) LOCAL_DB="$2"; shift 2 ;;
    --local-root-pass) LOCAL_ROOT_PASS="$2"; shift 2 ;;
    --local-app-user) LOCAL_APP_USER="$2"; shift 2 ;;
    --local-app-pass) LOCAL_APP_PASS="$2"; shift 2 ;;
    --local-tz) LOCAL_TZ="$2"; shift 2 ;;
    --spring-profile) SPRING_PROFILE="$2"; shift 2 ;;
    --app-base-url) LOCAL_APP_BASE_URL="$2"; LOCAL_REDIRECT_URI=""; shift 2 ;;
    --docker-only) DOCKER_ONLY="true"; shift ;;
    --reset-db) RESET_DB="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    --) shift; MVN_ARGS=("$@"); break ;;
    *)
      echo "Opcao invalida: $1" >&2
      usage
      exit 1
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
APP_ROOT="${PROJECT_ROOT}/${APP_DIR}"
COMPOSE_PATH="${PROJECT_ROOT}/${COMPOSE_FILE}"

validate_token() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "$name deve conter apenas letras, numeros e underscore: $value" >&2
    exit 1
  fi
}

sql_string() {
  printf "%s" "$1" | sed "s/'/''/g"
}

validate_token "--local-db" "$LOCAL_DB"
validate_token "--local-app-user" "$LOCAL_APP_USER"

LOCAL_APP_BASE_URL="${LOCAL_APP_BASE_URL%/}"
if [[ ! "$LOCAL_APP_BASE_URL" =~ ^https://[^/]+$ ]]; then
  echo "--app-base-url deve ser uma origem HTTPS sem path: $LOCAL_APP_BASE_URL" >&2
  exit 1
fi
if [[ -z "$LOCAL_REDIRECT_URI" ]]; then
  LOCAL_REDIRECT_URI="${LOCAL_APP_BASE_URL}/oauth/callback"
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker nao encontrado" >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "docker daemon indisponivel; inicie o Docker e tente novamente" >&2
  exit 1
fi

if [[ ! -f "$COMPOSE_PATH" ]]; then
  echo "compose nao encontrado: $COMPOSE_PATH" >&2
  exit 1
fi

export MYSQL_LOCAL_PORT="$LOCAL_PORT"
export MYSQL_LOCAL_ROOT_PASSWORD="$LOCAL_ROOT_PASS"
export MYSQL_LOCAL_TZ="$LOCAL_TZ"

cd "$PROJECT_ROOT"

if [[ "$RESET_DB" == "true" ]]; then
  echo "[docker] reset mysql-local-data"
  docker compose -f "$COMPOSE_PATH" down -v --remove-orphans
fi

echo "[docker] mysql-local"
docker compose -f "$COMPOSE_PATH" up -d --wait

APP_PASS_SQL="$(sql_string "$LOCAL_APP_PASS")"

echo "[db] ${LOCAL_DB}"
docker compose -f "$COMPOSE_PATH" exec -T mysql-local mysql -uroot "-p${LOCAL_ROOT_PASS}" <<SQL
CREATE DATABASE IF NOT EXISTS \`${LOCAL_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${LOCAL_APP_USER}'@'%' IDENTIFIED BY '${APP_PASS_SQL}';
ALTER USER '${LOCAL_APP_USER}'@'%' IDENTIFIED BY '${APP_PASS_SQL}';
GRANT ALL PRIVILEGES ON \`${LOCAL_DB}\`.* TO '${LOCAL_APP_USER}'@'%';
FLUSH PRIVILEGES;
SQL

export MYSQL_DOCKER_USER="$LOCAL_APP_USER"
export MYSQL_DOCKER_PASS="$LOCAL_APP_PASS"
export MYSQL_DOCKER_URL="jdbc:mysql://127.0.0.1:${LOCAL_PORT}/${LOCAL_DB}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048&serverTimezone=America/Sao_Paulo"

export DB_HOST="127.0.0.1"
export DB_PORT="$LOCAL_PORT"
export DB_NAME="$LOCAL_DB"
export DB_USER="$LOCAL_APP_USER"
export DB_PASSWORD="$LOCAL_APP_PASS"
export DB_URL="$MYSQL_DOCKER_URL"
export DB_USERNAME="$LOCAL_APP_USER"
export MYSQL_URL="$MYSQL_DOCKER_URL"
export MYSQL_USER="$LOCAL_APP_USER"
export MYSQL_PASSWORD="$LOCAL_APP_PASS"
export MYSQL_PASS="$LOCAL_APP_PASS"
export DATABASE_CONN="$MYSQL_DOCKER_URL"
export DATABASE_USER="$LOCAL_APP_USER"
export DATABASE_PASS="$LOCAL_APP_PASS"
export CONECTME_DATASOURCE_URL="$MYSQL_DOCKER_URL"
export CONECTME_DATASOURCE_USERNAME="$LOCAL_APP_USER"
export CONECTME_DATASOURCE_PASSWORD="$LOCAL_APP_PASS"
export SPRING_PROFILES_ACTIVE="$SPRING_PROFILE"
export APP_BASE_URL="$LOCAL_APP_BASE_URL"
export NUVEMSHOP_REDIRECT_URI="$LOCAL_REDIRECT_URI"

if [[ "$DOCKER_ONLY" == "true" ]]; then
  echo "[ok] docker pronto"
  exit 0
fi

if [[ ! -f "${APP_ROOT}/pom.xml" ]]; then
  echo "pom.xml nao encontrado em ${APP_ROOT}; banco preparado, mas nao foi possivel subir o sistema" >&2
  exit 1
fi

cd "$APP_ROOT"

if [[ -x "./mvnw" ]]; then
  MVN_CMD=("./mvnw")
elif command -v mvn >/dev/null 2>&1; then
  MVN_CMD=("mvn")
else
  echo "maven nao encontrado" >&2
  exit 1
fi

MVN_SETTINGS_ARG=()
if [[ -f "${PROJECT_ROOT}/scripts/maven-central-settings.xml" ]]; then
  MVN_SETTINGS_ARG=("-s" "${PROJECT_ROOT}/scripts/maven-central-settings.xml")
fi

echo "[spring] perfil=${SPRING_PROFILE} db=${LOCAL_DB} app_base_url=${APP_BASE_URL} redirect_uri=${NUVEMSHOP_REDIRECT_URI}"
exec "${MVN_CMD[@]}" "${MVN_SETTINGS_ARG[@]}" -Dspring-boot.plugin.skip=false -Dspring-boot.run.profiles="$SPRING_PROFILE" spring-boot:run "${MVN_ARGS[@]}"
