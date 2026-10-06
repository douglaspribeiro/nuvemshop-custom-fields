# Preços públicos por país

As páginas `/`, `/precos/` e `/termos/` usam o mesmo contexto de país.
Os preços são lidos de `nuvemshop.billing.prices` no `application.yml`, incluindo
os overrides das variáveis de ambiente existentes. Países sem tabela própria
usam a tabela `US`, em USD. Não há conversão de câmbio automática.

A escolha segue esta ordem:

1. Seleção manual pelo formulário `País da loja`, salva na sessão.
2. Seleção manual anterior da mesma sessão.
3. País da loja Nuvemshop autenticada.
4. Cabeçalho `CF-IPCountry` enviado pela Cloudflare.
5. Região do `Accept-Language` do navegador; sem região, português usa Brasil e
   os demais idiomas usam USD. Sem esses sinais, o padrão é Brasil.

Na Cloudflare, a geolocalização IP deve enviar `CF-IPCountry` ao origin para que
a detecção geográfica funcione. Sem esse cabeçalho, a detecção pelo navegador é
uma aproximação e o visitante pode corrigir a escolha no formulário. Os valores
`XX` e `T1` não são tratados como países. As respostas personalizadas recebem
`Cache-Control: private, no-store` para evitar compartilhar preços entre visitantes.

Os termos e os planos mostram Efí para o Brasil e Creem como provedor previsto
para os demais países. Isso não implementa a integração Creem nem muda o
roteamento de cobranças: essa integração ainda precisa ser desenvolvida e
ativada. O texto internacional informa essa indisponibilidade.

O e-mail de suporte padrão é `contato@wzhub.pro`, nas páginas de contato, termos,
central de suporte e ajuda do painel. Se `SUPPORT_EMAIL` estiver definido no
ambiente, ele prevalece e deve ser atualizado para o mesmo endereço.

Validação: `mvn -o -Dskip.frontend=true -Dtest=PublicMarketPagesTest,StoreLastAccessTest test`.
