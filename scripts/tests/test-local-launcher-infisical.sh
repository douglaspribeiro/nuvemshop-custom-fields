#!/usr/bin/env bash
set -euo pipefail
TEST_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LAUNCHER="${TEST_SCRIPT_DIR}/../start-local-docker-and-spring.sh"
export PATH="${TEST_SCRIPT_DIR}/fixtures/local-launcher:$PATH"
# Nunca invocar um Docker, Maven ou Infisical real neste teste.
for tool in docker mvn infisical; do
  [[ "$(command -v "$tool")" == "${TEST_SCRIPT_DIR}/fixtures/local-launcher/$tool" ]]
done
unset LOCAL_HOMOLOG_ACCESS_KEY LOCAL_INFISICAL_PROJECT_ID LOCAL_INFISICAL_ENV LOCAL_INFISICAL_PATH

output="$(bash "$LAUNCHER" --infisical --homolog-local \
  --app-base-url https://local-tunnel.invalid -- '-Dtest.argument=valor com espacos')"
[[ "$output" == *fake:infisical*fake:docker*fake:mvn* ]]
[[ "$(printf '%s\n' "$output" | rg -c '^fake:infisical$')" == 1 ]]
printf 'OK: injecao antes do Docker, sem recursao, env e argumentos preservados\n'

output="$(LAUNCHER_TEST_EXPECT_URL=http://localhost:8080 bash "$LAUNCHER" \
  --homolog-local --infisical -- '-Dtest.argument=valor com espacos')"
[[ "$output" == *fake:infisical*fake:docker*fake:mvn* ]]
printf 'OK: localhost padrao, sem URL remota de stage, com cookies HTTP locais\n'

output="$(LAUNCHER_TEST_EXPECT_URL=http://127.0.0.1:8080 bash "$LAUNCHER" \
  --homolog-local --infisical --app-base-url http://127.0.0.1:8080 -- '-Dtest.argument=valor com espacos')"
[[ "$output" == *fake:mvn* ]]
printf 'OK: HTTP explicitamente permitido somente no loopback de homologacao\n'

output="$(bash "$LAUNCHER" --homolog-local --infisical --docker-only)"
[[ "$output" == *fake:infisical*fake:docker*'[ok] docker pronto'* ]]
[[ "$output" != *fake:mvn* ]]
printf 'OK: docker-only funciona sob Infisical\n'

set +e
output="$(LAUNCHER_TEST_FAIL=true bash "$LAUNCHER" --homolog-local --infisical --docker-only 2>&1)"
status=$?
set -e
[[ "$status" == 73 && "$output" != *fake:docker* ]]
printf 'OK: falha do Infisical aborta antes do Docker\n'

for args in '--infisical' '--infisical --homolog-local --reset-db' '--infisical --homolog-local --infisical-env production' '--infisical --homolog-local --infisical-path relative' '--infisical --infisical-env'; do
  read -r -a test_args <<< "$args"
  if output="$(bash "$LAUNCHER" "${test_args[@]}" 2>&1)"; then
    printf 'Falhou: entrada invalida aceita: %s\n' "$args" >&2; exit 1
  fi
  [[ "$output" != *fake:infisical* && "$output" != *fake:docker* ]]
done
printf 'OK: configuracoes inseguras/invalidas rejeitadas antes dos servicos\n'

for args in '--app-base-url http://localhost:8080' '--homolog-local --app-base-url http://remote.invalid' '--homolog-local --app-base-url http://localhost:8080/path'; do
  read -r -a test_args <<< "$args"
  if output="$(LOCAL_HOMOLOG_ACCESS_KEY=local-test-key-with-more-than-24-characters bash "$LAUNCHER" "${test_args[@]}" 2>&1)"; then
    printf 'Falhou: URL insegura aceita: %s\n' "$args" >&2; exit 1
  fi
  [[ "$output" != *fake:docker* ]]
done
printf 'OK: HTTP remoto e HTTP fora do perfil local rejeitados\n'
