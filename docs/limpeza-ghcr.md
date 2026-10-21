# Retenção de imagens no GHCR

`scripts/push-docker.sh` publica `latest` e a tag da versão. O histórico do GHCR
não é removido pelo Docker local nem pela retenção de imagens no servidor.
A limpeza após cada publicação usa somente o pacote pessoal
`ghcr.io/$GHCR_USER/nuvemshop-custom-fields` (não outros pacotes nem organizações).

`push-docker.sh` executa a exclusão após toda publicação por padrão. Exige
uma credencial do `docker login ghcr.io` ou `GHCR_TOKEN`, além de
`GHCR_PROTECTED_REFS_FILE` (ou `--ghcr-protected-refs`) antes do build.
`--cleanup-ghcr` troca a exclusão por uma simulação nessa execução.
O workflow `.github/workflows/deploy.yml` apenas publica: não chama este helper.
A retenção padrão mantém as últimas 20 imagens publicadas (versões com tag),
sem proteção adicional por idade. Tags de teste também entram nesse limite.
`latest` e referências explícitas de produção/rollback continuam protegidas.
Versões sem tag podem ser manifests de plataforma ou atestações necessários
para imagens preservadas e não devem ser apagadas apenas por não terem tag.

## Simular

O helper usa `GHCR_TOKEN`, se definido; caso contrário, lê a credencial salva
pelo `docker login ghcr.io`, inclusive via credential helper. Não imprime a credencial.
Use um PAT clássico com `read:packages` para consultar e também `delete:packages`
quando for excluir; o usuário precisa administrar o pacote. Ter permissão de push
não garante permissão de exclusão. Não inclua tokens em arquivos versionados ou mensagens.

```bash
# Sem build, push ou exclusão:
python3 scripts/cleanup-ghcr.py

# Publica normalmente e depois simula a limpeza:
bash scripts/push-docker.sh --cleanup-ghcr

# Ajusta a quantidade; o padrão é 20 imagens, sem proteção por idade:
bash scripts/push-docker.sh --cleanup-ghcr --ghcr-keep 10
```

O plano lista IDs das versões de pacote, digests e tags. Não estima espaço em
bytes: camadas compartilhadas e a coleta do registry influenciam o espaço liberado.
Sem arquivo de proteção, a simulação é preliminar e não sabe quais versões
antigas estão fixadas no servidor por digest.

## Proteger produção e rollback

Antes de excluir, confira os servidores e inclua TODAS as referências necessárias:
containers ativos/parados preservados, últimas imagens aprovadas, journal de
deploy/recuperação e baseline de rollback. No deploy validado, os IDs Docker de
`state.json`, `approved-images.json` e dos snapshots de recuperação precisam ser
resolvidos para os **digests OCI em `RepoDigests`** ou para suas tags GHCR.
O ID de configuração retornado como `docker image inspect .Id` não é o digest
do manifest do registry e não deve ser usado como substituto.

Prepare fora do Git um JSON com a imagem exata, a data real da inspeção em UTC
e suas tags/digests protegidos. Exemplo de formato (substituir pelos dados da
inspeção atual, inclusive a data; não copiar como configuração pronta):

```json
{
  "image": "ghcr.io/douglaspribeiro/nuvemshop-custom-fields",
  "captured_at": "2026-10-20T22:00:00Z",
  "protected_refs": ["latest", "v1.41.0", "v1.40.7", "TAG_OU_DIGEST_DO_BASELINE"]
}
```

O helper exige captura com menos de 24 horas, escopo exato e referências
existentes. Ele não consulta os servidores para comprovar que a lista está
completa: essa conferência é responsabilidade de quem prepara o arquivo.
Atualize a captura imediatamente antes da limpeza. Não execute simultaneamente
com outra publicação, alteração de tag ou rollback.

```bash
# Revise primeiro o plano com as proteções reais:
python3 scripts/cleanup-ghcr.py --protected-refs-file /tmp/ncf-ghcr-protected.json

# Excluir somente depois de revisar:
python3 scripts/cleanup-ghcr.py --protected-refs-file /tmp/ncf-ghcr-protected.json --apply

# Ou após uma publicação:
bash scripts/push-docker.sh --cleanup-ghcr-apply \
  --ghcr-protected-refs /tmp/ncf-ghcr-protected.json
```

`GHCR_PROTECTED_REFS_FILE` fornece o caminho padrão. No helper isolado, excluir
continua exigindo `--apply`; no `push-docker.sh`, a exclusão já é o padrão.
No push, a tag publicada e `latest` também são protegidas.

## Regras de segurança

- Mantém as N imagens com tag mais recentemente criadas (20 por padrão).
  Não preserva versões extras por idade; `--ghcr-min-age-days N` / `--min-age-days N`
  permite solicitar essa proteção adicional, cujo padrão é zero.
- `latest` e referências explícitas de produção/rollback permanecem protegidas.
  Tags antigas de teste ou `rollback` sem proteção explícita podem ser removidas.
  Uma imagem com várias tags conta uma vez e permanece se qualquer tag for protegida.
- Percorre os índices OCI/Docker das imagens mantidas e preserva seus manifests
  de plataforma e atestações, mesmo sem tag. Somente manifests sem referência
  nas imagens preservadas podem entrar no plano de remoção.
- Lista todas as páginas antes de planejar. Falha de autenticação, referência
  protegida ausente ou manifest desconhecido interrompe a operação.
- Antes de excluir, confirma o proprietário do token e que o inventário não
  mudou. Cada versão é conferida novamente antes do DELETE. Uma mudança
  concorrente detectada interrompe o restante, sem desfazer exclusões já feitas.
- A exclusão remove a versão do pacote GHCR, incluindo suas tags. Não altera tags
  Git, containers, volumes ou imagens Docker locais. Para recuperar uma exclusão,
  consulte as condições de restauração do GitHub Packages.
- Se a limpeza após o push falhar, a imagem nova já foi publicada. O script
  informa isso e retorna erro; execute o helper separadamente, sem gerar outra release.

Referências: [API de pacotes](https://docs.github.com/en/rest/packages/packages),
[excluir/restaurar pacotes](https://docs.github.com/en/packages/learn-github-packages/deleting-and-restoring-a-package).
