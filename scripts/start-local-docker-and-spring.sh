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
APP_BASE_URL_EXPLICIT="false"
DOCKER_ONLY="false"
RESET_DB="false"
LOCAL_HOMOLOG="false"
USE_INFISICAL="false"
# Identificadores publicos do projeto; credenciais ficam no Infisical/CLI.
LOCAL_INFISICAL_PROJECT_ID="${LOCAL_INFISICAL_PROJECT_ID:-831846ed-254b-4e13-b763-f124da86d11f}"
LOCAL_INFISICAL_ENV="${LOCAL_INFISICAL_ENV:-stage}"
LOCAL_INFISICAL_PATH="${LOCAL_INFISICAL_PATH:-/app_custom-fields}"
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
  --app-base-url URL          Origem do app (homolog-local: http://localhost:8080)
  --docker-only               Sobe/prepara apenas o MySQL
  --homolog-local             Banco separado, perfil docker,local-homolog e entrada de loja ficticia
  --infisical                 Carrega stage do Infisical antes do MySQL (exige --homolog-local)
  --infisical-project-id ID   Projeto Infisical (padrao: 831846ed-254b-4e13-b763-f124da86d11f)
  --infisical-env SLUG        Ambiente Infisical (padrao: stage; producao nao permitida)
  --infisical-path PATH       Pasta Infisical (padrao: /app_custom-fields)
  --reset-db                  Recria o volume compartilhado mysql-local-data
  -h, --help                  Mostra esta ajuda

Exemplo:
  ./scripts/start-local-docker-and-spring.sh -- -DskipTests
EOF
}

MVN_ARGS=()
REEXEC_ARGS=()

while [[ $# -gt 0 ]]; do
  # O processo filho recebe todas as opcoes, exceto as do wrapper Infisical.
  # Isso preserva argumentos com espacos e impede chamadas recursivas do CLI.
  case "$1" in
    --infisical) ;;
    --infisical-project-id|--infisical-env|--infisical-path)
      if [[ $# -lt 2 || -z "$2" || "$2" == --* ]]; then
        echo "$1 exige um valor." >&2; exit 1
      fi
      ;;
    --) REEXEC_ARGS+=("$@"); ;;
    --local-port|--local-db|--local-root-pass|--local-app-user|--local-app-pass|--local-tz|--spring-profile|--app-base-url)
      if [[ $# -lt 2 ]]; then echo "$1 exige um valor." >&2; exit 1; fi
      REEXEC_ARGS+=("$1" "$2") ;;
    *) REEXEC_ARGS+=("$1") ;;
  esac
  case "$1" in
    --infisical) USE_INFISICAL="true"; shift ;;
    --infisical-project-id) LOCAL_INFISICAL_PROJECT_ID="$2"; shift 2 ;;
    --infisical-env) LOCAL_INFISICAL_ENV="$2"; shift 2 ;;
    --infisical-path) LOCAL_INFISICAL_PATH="$2"; shift 2 ;;
    --local-port) LOCAL_PORT="$2"; shift 2 ;;
    --local-db) LOCAL_DB="$2"; shift 2 ;;
    --local-root-pass) LOCAL_ROOT_PASS="$2"; shift 2 ;;
    --local-app-user) LOCAL_APP_USER="$2"; shift 2 ;;
    --local-app-pass) LOCAL_APP_PASS="$2"; shift 2 ;;
    --local-tz) LOCAL_TZ="$2"; shift 2 ;;
    --spring-profile) SPRING_PROFILE="$2"; shift 2 ;;
    --app-base-url) LOCAL_APP_BASE_URL="$2"; LOCAL_REDIRECT_URI=""; APP_BASE_URL_EXPLICIT="true"; shift 2 ;;
    --docker-only) DOCKER_ONLY="true"; shift ;;
    --homolog-local) LOCAL_HOMOLOG="true"; shift ;;
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

if [[ "$USE_INFISICAL" == "true" ]]; then
  if [[ "$LOCAL_HOMOLOG" != "true" || "$RESET_DB" == "true" ]]; then
    echo "--infisical exige --homolog-local e nao permite --reset-db." >&2; exit 1
  fi
  if [[ ! "$LOCAL_INFISICAL_PROJECT_ID" =~ ^[A-Za-z0-9_-]+$ ||
        ! "$LOCAL_INFISICAL_ENV" =~ ^[A-Za-z0-9_-]+$ ||
        "$LOCAL_INFISICAL_PATH" != /* ]]; then
    echo "Informe projeto, ambiente e pasta absoluta validos para o Infisical." >&2; exit 1
  fi
  case "${LOCAL_INFISICAL_ENV,,}" in
    prod|production|producao)
      echo "Este launcher de homologacao nao permite ambiente Infisical de producao." >&2; exit 1 ;;
  esac
  if ! command -v infisical >/dev/null 2>&1; then
    echo "infisical nao encontrado; instale o CLI e autentique com infisical login." >&2; exit 1
  fi
  echo "[infisical] carregando ambiente ${LOCAL_INFISICAL_ENV} antes do Docker"
  # Reinicia o launcher para que os defaults sejam calculados com o env injetado.
  # Falhas de autenticacao/leitura encerram o fluxo, sem iniciar Docker/MySQL.
  exec infisical run --projectId="$LOCAL_INFISICAL_PROJECT_ID" \
    --env="$LOCAL_INFISICAL_ENV" --path="$LOCAL_INFISICAL_PATH" \
    -- bash "${BASH_SOURCE[0]}" "${REEXEC_ARGS[@]}"
fi

if [[ "$LOCAL_HOMOLOG" == "true" ]]; then
  SPRING_PROFILE="docker,local-homolog"
  # Nunca reaproveitar a URL/callback remoto de stage na loja ficticia local.
  if [[ "$APP_BASE_URL_EXPLICIT" == "false" ]]; then
    LOCAL_APP_BASE_URL="http://localhost:8080"
  fi
  LOCAL_REDIRECT_URI=""
  if [[ "$LOCAL_DB" == "nuvem_custom_fields" ]]; then LOCAL_DB="nuvem_custom_fields_homolog"; fi
  if [[ "$LOCAL_APP_USER" == "nuvem_custom_fields" ]]; then LOCAL_APP_USER="nuvem_custom_fields_homolog"; fi
  if [[ "$LOCAL_DB" != *_homolog ]]; then
    echo "--homolog-local exige um banco terminado em _homolog." >&2; exit 1
  fi
  LOCAL_HOMOLOG_KEY="${LOCAL_HOMOLOG_ACCESS_KEY:-}"
  if [[ ${#LOCAL_HOMOLOG_KEY} -lt 24 || "$LOCAL_HOMOLOG_KEY" == SUBSTITUA* ]]; then
    echo "Defina LOCAL_HOMOLOG_ACCESS_KEY com pelo menos 24 caracteres antes de iniciar." >&2; exit 1
  fi
  if [[ "$RESET_DB" == "true" ]]; then
    echo "--homolog-local nao permite --reset-db: o volume MySQL e compartilhado com outros bancos locais." >&2; exit 1
  fi
fi

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
if [[ "$LOCAL_APP_BASE_URL" =~ ^https://[^/]+$ ]]; then
  if [[ "$LOCAL_HOMOLOG" == "true" ]]; then export LOCAL_HOMOLOG_COOKIE_SECURE=true; fi
elif [[ "$LOCAL_HOMOLOG" == "true" && "$LOCAL_APP_BASE_URL" =~ ^http://(localhost|127\.0\.0\.1)(:[0-9]+)?$ ]]; then
  export LOCAL_HOMOLOG_COOKIE_SECURE=false
else
  echo "--app-base-url exige origem HTTPS; homolog-local tambem permite HTTP em localhost/127.0.0.1, sem path." >&2
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
