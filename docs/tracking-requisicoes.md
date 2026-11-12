# Tracking de operações HTTP

POST, PUT, PATCH e DELETE produzem logs `INFO` de início (`request.start`) e resultado (`request.end`) sem habilitar DEBUG. O mesmo UUID aparece em `request_id`, no MDC `requestId` e no cabeçalho de resposta `X-Request-Id`. Um cabeçalho recebido só é reutilizado quando é UUID válido; valores inválidos não são registrados.

O resultado informa método, rota parametrizada quando disponível, ação Controller.método, loja, produto/campo quando identificáveis, status HTTP, duração e outcome. Loja vem da sessão autenticada ou de uma variável numérica da rota; uma loja solicitada na URL não comprova autorização.

- `COMPLETED`: operação concluída com resposta abaixo de 300.
- `REDIRECTED`: redirecionamento sem erro identificado; não comprova a conclusão de uma operação de negócio.
- `REJECTED` em WARN: HTTP 4xx, exceção tratada pelo MVC ou validação/erro informado na tela/flash, inclusive HTTP 200/302.
- `ERROR` em ERROR: HTTP 5xx ou exceção não tratada, incluindo erro/timeout assíncrono. O status é o valor observado da resposta no momento do log; uma exceção pode ocorrer antes de o contêiner definir o status final.

Exceções tratadas por `@ExceptionHandler` passam pelo observador sem alterar o tratamento/resposta. A classe da causa e a localização no código ajudam a localizar o problema; códigos conhecidos de rejeição de upload (por exemplo `upload.quota.exceeded`) também aparecem. Não se registram mensagem bruta/stacktrace da exceção, corpo, arquivos, query string, cookies, IDs de sessão, credenciais ou cabeçalho inválido. Links winback e segmentos opacos são mascarados.

GETs bem-sucedidos continuam em DEBUG para não gerar ruído na vitrine, assets e métricas. Falhas de GET e dos demais métodos aparecem em WARN/ERROR. Requisições assíncronas registram o resultado na conclusão, erro ou timeout, sem repetir o mesmo resultado no callback de conclusão. O tracking não cria sessões e preserva/restaura o MDC do chamador.

Para investigar no Loki, selecione os logs do app e filtre `|= "request_id=UUID"`. Para todas as alterações, filtre `|= "request.start"` ou `|= "request.end"`; problemas podem ser encontrados por `outcome=REJECTED` ou `outcome=ERROR`. O `request_id` também permite correlacionar os logs de negócio emitidos no mesmo fluxo síncrono.

Este tracking cobre a fronteira HTTP. Exceções capturadas e silenciosamente descartadas dentro de um método, sem sinalizar falha na resposta/modelo/flash, precisam de log no ponto de captura. Jobs e integrações de saída mantêm seus logs próprios; este filtro não afirma instrumentação global de toda chamada interna ou tarefa em segundo plano.
