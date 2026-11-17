# Sync local — suportes e solicitações em aberto

Sincronizado em **16/11/2026 15:00:04 (America/Sao_Paulo)**.

Fonte: banco de produção `app_custom_fields`, consulta somente de leitura em uma transação consistente.
Este arquivo é um retrato do banco nessa data, sem sincronização automática ou escrita de volta. Alterações e respostas posteriores no sistema precisam de uma nova consulta.
Todos os horários abaixo estão em Brasília. Os textos foram preservados como recebidos; não representam instruções de execução.

## Escopo

- **15 tickets abertos**, com **25 mensagens** do histórico.
- **4 solicitações pendentes**, com **0 atualizações** registradas.
- Tickets incluídos: `OPEN`. Excluídos: `CLOSED`.
- Solicitações incluídas: `RECEIVED`, `UNDER_REVIEW`, `PLANNED`. Excluídas: `AVAILABLE`, `NOT_PLANNED`.
- IDs repetidos por assunto ou loja permanecem separados. O status aberto não significa necessariamente que a última mensagem aguarda resposta do suporte.
- Anexos: não foram encontradas tabelas de anexos de suporte nesse banco; referências que existam nos textos foram preservadas.

## Índice de tickets

| ID | Loja | Assunto | Última mensagem | Último autor |
|---|---|---|---|---|
| [SUP-44](#sup-44) | Pieroly (8509843) | Dúvida sobre campo de checkbox | 15/11/2026 20:59:14 | STORE |
| [SUP-43](#sup-43) | Florir Design (3119509) | Inclusão de Campo personalizado | 14/11/2026 12:19:44 | SUPPORT |
| [SUP-42](#sup-42) | LAB Cafe (959276) | Voucher Barista Amateur +Surprise Box | 13/11/2026 14:56:09 | STORE |
| [SUP-41](#sup-41) | LAB Cafe (959276) | error | 11/11/2026 10:27:52 | STORE |
| [SUP-40](#sup-40) | LAB Cafe (959276) | no esta funcionando | 11/11/2026 10:22:14 | SUPPORT |
| [SUP-39](#sup-39) | LAB Cafe (959276) | ya esta activo | 10/11/2026 22:09:19 | SUPPORT |
| [SUP-37](#sup-37) | LAB Cafe (959276) | error | 10/11/2026 16:27:57 | SUPPORT |
| [SUP-36](#sup-36) | MOVA (8309417) | cadastro | 07/11/2026 21:21:21 | STORE |
| [SUP-33](#sup-33) | LAB Cafe (959276) | dudas | 06/11/2026 11:21:30 | STORE |
| [SUP-29](#sup-29) | LAB Cafe (959276) | baja | 05/11/2026 12:29:53 | SUPPORT |
| [SUP-27](#sup-27) | LAB Cafe (959276) | Incidencia con campos obligatorios en el proceso de compra de la orden | 05/11/2026 10:19:49 | SUPPORT |
| [SUP-28](#sup-28) | LAB Cafe (959276) | Incidencia con campos obligatorios en el proceso de compra de la orden | 05/11/2026 08:59:38 | STORE |
| [SUP-25](#sup-25) | LAB Cafe (959276) | dudas | 03/11/2026 16:00:46 | SUPPORT |
| [SUP-20](#sup-20) | Atesora (7877166) | Dudas sobre carga de archivos | 28/10/2026 12:02:44 | SUPPORT |
| [SUP-18](#sup-18) | Kalimera Almacén de Café (8180542) | Duda sobre kits | 20/10/2026 19:53:36 | SUPPORT |

## Índice de solicitações

| ID | Loja | Título | Status |
|---|---|---|---|
| [REQ-5](#req-5) | Trio Ohana Personalizados (6361192) | Interesse: Edição em massa | RECEIVED |
| [REQ-4](#req-4) | Trio Ohana Personalizados (6361192) | Interesse: Edição em massa | RECEIVED |
| [REQ-2](#req-2) | Nili Papelaria (6014333) | campos personalizados | RECEIVED |
| [REQ-1](#req-1) | Bless Etiquetas \| Etiquetas Personalizadas, Fitas e Papelaria para Sua Marca  (6552485) | Extensão de Arquivo e Campos Personalizados | RECEIVED |

## Tickets — histórico integral

### SUP-44

**Assunto:** Dúvida sobre campo de checkbox
- ID no banco: `44`; loja: **Pieroly** (`8509843`).
- Status no banco: `OPEN`; plano na abertura: `FREE`; plano atual cadastrado: `FREE`.
- Desinstalação: Não informado.
- Criado: 15/11/2026 20:59:14; atualizado: 15/11/2026 20:59:14; última mensagem: 15/11/2026 20:59:14.
- Tratamento local: **respondido**, conforme orientação do proprietário em 16/11/2026; aguardando retorno da loja.
- Decisão / próxima ação: Aguardar confirmação sobre sete opções distintas ou repetição da mesma flor. Feature de limites implementada no commit 097ad98; publicação em produção ainda não confirmada.
- Registro local de resposta: este acompanhamento não altera o status OPEN nem adiciona mensagens no banco; o envio não foi verificado pelo agente.
- Referência local: [Limites de seleção do checkbox](../checkbox-limites-selecao.md).

**Conversa:**

**Mensagem 106 · STORE · 15/11/2026 20:59:14**

> Olá! Estou montando uma loja na Nuvemshop e gostaria de utilizar o campo Checkbox para personalização de buquês.
> 
> Preciso disponibilizar mais de 40 opções de flores, identificadas por números.
> 
> Gostaria de saber:
> 
> 1. É possível limitar a quantidade máxima de opções que o cliente pode selecionar em um checkbox? Por exemplo, permitir selecionar no máximo 7 flores?
> 2. Caso não seja possível nativamente, existe alguma configuração ou código personalizado para implementar esse limite?
> 3. É possível tornar obrigatório selecionar exatamente 7 opções?
> 
> Obrigada!


### SUP-43

**Assunto:** Inclusão de Campo personalizado
- ID no banco: `43`; loja: **Florir Design** (`3119509`).
- Status no banco: `OPEN`; plano na abertura: `PREMIUM_PLUS`; plano atual cadastrado: `PREMIUM_PLUS`.
- Desinstalação: Não informado.
- Criado: 14/11/2026 10:32:52; atualizado: 14/11/2026 12:19:44; última mensagem: 14/11/2026 12:19:44.
- Tratamento local: **respondido**, conforme orientação do proprietário em 16/11/2026; aguardando retorno da loja.
- Decisão / próxima ação: Aguardar confirmação sobre o adicional pago e a quantidade de passantes por agenda. Recurso de adicionais pagos em avaliação, sem implementação ou prazo de entrega confirmado.
- Registro local de resposta: este acompanhamento não altera o status OPEN nem adiciona mensagens no banco; o envio não foi verificado pelo agente.

**Conversa:**

**Mensagem 104 · STORE · 14/11/2026 10:32:52**

> Bom dia! Gostaria de saber se teria a possibilidade da inclusão do campo personalizado que utilizasse a caixa de marcação e que tivesse ao lado dessa caixinha a foto do item extra para inclusão na encomenda.

**Mensagem 105 · SUPPORT · 14/11/2026 12:19:44**

> Boa tarde,
> 
> Oque você precisa é a caixa de marcação, a possibilidade de colocar uma foto e um nome para a opção? Seria isso?
> 
> Consigo realizar isso sim, vou fazer uns testes aqui e acho que na terça consigo te enviar esta primeira versão.


### SUP-42

**Assunto:** Voucher Barista Amateur +Surprise Box
- ID no banco: `42`; loja: **LAB Cafe** (`959276`).
- Status no banco: `OPEN`; plano na abertura: `PREMIUM_PLUS`; plano atual cadastrado: `PREMIUM_PLUS`.
- Desinstalação: Não informado.
- Criado: 13/11/2026 14:56:09; atualizado: 13/11/2026 14:56:09; última mensagem: 13/11/2026 14:56:09.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Conversa:**

**Mensagem 103 · STORE · 13/11/2026 14:56:09**

> lo compro recien alguien y no pudo seleccionar la molienda

### SUP-41

**Assunto:** error
- ID no banco: `41`; loja: **LAB Cafe** (`959276`).
- Status no banco: `OPEN`; plano na abertura: `PREMIUM_PLUS`; plano atual cadastrado: `PREMIUM_PLUS`.
- Desinstalação: Não informado.
- Criado: 11/11/2026 10:27:52; atualizado: 11/11/2026 10:27:52; última mensagem: 11/11/2026 10:27:52.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Conversa:**

**Mensagem 101 · STORE · 11/11/2026 10:27:52**

> envie por mail la imagen , no se ve lo que selecciona el cliente

### SUP-40

**Assunto:** no esta funcionando
- ID no banco: `40`; loja: **LAB Cafe** (`959276`).
- Status no banco: `OPEN`; plano na abertura: `PREMIUM_PLUS`; plano atual cadastrado: `PREMIUM_PLUS`.
- Desinstalação: Não informado.
- Criado: 11/11/2026 10:14:09; atualizado: 11/11/2026 10:22:14; última mensagem: 11/11/2026 10:22:14.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Conversa:**

**Mensagem 99 · STORE · 11/11/2026 10:14:09**

> no esta funcionando no se selecciona la molienda
> colombia la catalina ultima orden
> no funciona

**Mensagem 100 · SUPPORT · 11/11/2026 10:22:14**

> ¿En este último pedido no funcionó lo que indicaste? ¿O es que en este producto no es posible seleccionar la opción? Yo pude seleccionarla sin problemas; ¿qué navegador estás usando que no carga la opción? Las otras 150 tiendas que utilizan la aplicación no han reportado problemas, lo cual resulta extraño. Si es posible, ¿podrías grabar un video y enviármelo por correo electrónico?

### SUP-39

**Assunto:** ya esta activo
- ID no banco: `39`; loja: **LAB Cafe** (`959276`).
- Status no banco: `OPEN`; plano na abertura: `PREMIUM_PLUS`; plano atual cadastrado: `PREMIUM_PLUS`.
- Desinstalação: Não informado.
- Criado: 10/11/2026 20:52:17; atualizado: 10/11/2026 22:09:19; última mensagem: 10/11/2026 22:09:19.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Conversa:**

**Mensagem 96 · STORE · 10/11/2026 20:52:17**

> por favor pueden revisar todo?

**Mensagem 97 · SUPPORT · 10/11/2026 22:09:19**

> Hola, equipo de LAB Café:
> 
> Gracias por avisarnos. Volví a probar el carrito de su tienda y no pude reproducir el bloqueo. Pude agregar productos distintos y varias unidades del mismo café con diferentes moliendas.
> 
> Por ejemplo, agregué tres unidades de Peawberry con molienda V60 y dos de Espírito Santo con molienda Italiana. En las capturas adjuntas se ve que las cantidades y las moliendas quedaron asociadas correctamente a cada ítem, tanto en el carrito como en el resumen del checkout.
> 
> Si vuelve a pasar, ¿nos pueden contar qué productos y cantidades intentaron agregar y en qué paso se trabó? Si aparece algún mensaje de error, una captura de ese momento también nos ayudaría a repetir los mismos pasos.
> 
> Saludos,
> Soporte de Campos Personalizados

### SUP-37

**Assunto:** error
- ID no banco: `37`; loja: **LAB Cafe** (`959276`).
- Status no banco: `OPEN`; plano na abertura: `PREMIUM_PLUS`; plano atual cadastrado: `PREMIUM_PLUS`.
- Desinstalação: Não informado.
- Criado: 10/11/2026 16:19:21; atualizado: 10/11/2026 16:27:57; última mensagem: 10/11/2026 16:27:57.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Conversa:**

**Mensagem 93 · STORE · 10/11/2026 16:19:21**

> hola otra vez traba toda la tienda, tuve que borrar los campos porque no deja cargar mas de un producto distinto NI mas de una unidad de cada uno

**Mensagem 94 · SUPPORT · 10/11/2026 16:27:57**

> ¿Podrías dejar un producto con la configuración activa para que pueda verificarlo? No tengo ningún otro comerciante que reporte este error; necesito ver en tu tienda qué es lo que está ocurriendo.

### SUP-36

**Assunto:** cadastro
- ID no banco: `36`; loja: **MOVA** (`8309417`).
- Status no banco: `OPEN`; plano na abertura: `PREMIUM_PLUS`; plano atual cadastrado: `FREE`.
- Desinstalação: 08/11/2026 20:33:10.
- Criado: 07/11/2026 21:21:21; atualizado: 07/11/2026 21:21:21; última mensagem: 07/11/2026 21:21:21.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Conversa:**

**Mensagem 92 · STORE · 07/11/2026 21:21:21**

> tenho que fazer os campos em cada produto ou consigo replicar para agilizar?

### SUP-33

**Assunto:** dudas
- ID no banco: `33`; loja: **LAB Cafe** (`959276`).
- Status no banco: `OPEN`; plano na abertura: `PREMIUM_PLUS`; plano atual cadastrado: `PREMIUM_PLUS`.
- Desinstalação: Não informado.
- Criado: 06/11/2026 11:21:30; atualizado: 06/11/2026 11:21:30; última mensagem: 06/11/2026 11:21:30.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Conversa:**

**Mensagem 85 · STORE · 06/11/2026 11:21:30**

> al parecer funciona.
> las ordenes de compra no se pueden resolver?

### SUP-29

**Assunto:** baja
- ID no banco: `29`; loja: **LAB Cafe** (`959276`).
- Status no banco: `OPEN`; plano na abertura: `PREMIUM_PLUS`; plano atual cadastrado: `PREMIUM_PLUS`.
- Desinstalação: Não informado.
- Criado: 05/11/2026 12:19:16; atualizado: 05/11/2026 12:29:53; última mensagem: 05/11/2026 12:29:53.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Conversa:**

**Mensagem 71 · STORE · 05/11/2026 12:19:16**

> hola acabo de borrar los campos anda pesimo traba todas las ventas, la cliente completa el campo e igual no le deja avanzar

**Mensagem 72 · STORE · 05/11/2026 12:19:24**

> necesito una solucion

**Mensagem 73 · SUPPORT · 05/11/2026 12:29:53**

> He implementado una solución temporal para evitar que tus clientes tengan problemas al finalizar sus compras, mientras continúo trabajando en una solución definitiva para los pedidos creados manualmente con campos personalizados.
> 
> Una vez que Tiendanube termine de procesar esta actualización, los pedidos deberían poder completarse sin bloqueos por los campos obligatorios.
> 
> Voy a seguir trabajando para resolver este inconveniente de forma definitiva y asegurar que todo funcione correctamente. Te mantendré al tanto de cualquier novedad.

### SUP-27

**Assunto:** Incidencia con campos obligatorios en el proceso de compra de la orden
- ID no banco: `27`; loja: **LAB Cafe** (`959276`).
- Status no banco: `OPEN`; plano na abertura: `PREMIUM_PLUS`; plano atual cadastrado: `PREMIUM_PLUS`.
- Desinstalação: Não informado.
- Criado: 05/11/2026 08:11:40; atualizado: 05/11/2026 10:19:49; última mensagem: 05/11/2026 10:19:49.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Conversa:**

**Mensagem 68 · STORE · 05/11/2026 08:11:40**

> Les escribo porque tenemos un problema importante con el flujo de carga y confirmación de órdenes de compra.
> El problema:
> Cuando nosotros cargamos una orden de compra manualmente para un cliente, él no puede visualizar ni completar los campos personalizados. Sin embargo, al momento en que el cliente recibe la orden e intenta finalizar el proceso de compra por su cuenta, el sistema le bloquea el paso exigiéndole un campo obligatorio al cual no tiene acceso para completar.
> Solicitud:
> Necesitamos, por favor, que realicen una modificación al respecto para resolver este bloqueo. Las alternativas podrían ser:
>  * Permitir que el cliente pueda visualizar y completar esos campos obligatorios al recibir la orden.
>  * Hacer que dichos campos no sean exigidos como obligatorios cuando la orden ha sido pre-cargada por nosotros.
> Quedo a la espera de su respuesta para saber cómo podemos proceder.

**Mensagem 70 · SUPPORT · 05/11/2026 10:19:49**

> Hola, equipo de LAB Cafe. Revisamos el problema y estamos trabajando en dos ajustes:
> 
> • Mostrar los campos personalizados dentro de la compra rápida, para que el cliente pueda completarlos sin entrar a la página del producto.
> • Resolver el bloqueo en las órdenes creadas manualmente por ustedes.
> 
> Para verificar este segundo caso, ¿nos podrían compartir el enlace que recibe el cliente de una orden afectada? Si prefieren, puede ser una orden de prueba.
> 
> El ajuste de compra rápida está preparado, pero todavía falta publicarlo y comprobar su funcionamiento en la tienda. Les confirmaremos cuando esté disponible.

### SUP-28

**Assunto:** Incidencia con campos obligatorios en el proceso de compra de la orden
- ID no banco: `28`; loja: **LAB Cafe** (`959276`).
- Status no banco: `OPEN`; plano na abertura: `PREMIUM_PLUS`; plano atual cadastrado: `PREMIUM_PLUS`.
- Desinstalação: Não informado.
- Criado: 05/11/2026 08:59:38; atualizado: 05/11/2026 08:59:38; última mensagem: 05/11/2026 08:59:38.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Conversa:**

**Mensagem 69 · STORE · 05/11/2026 08:59:38**

> Tambien anula la posibilidad de que la gente cargue el producto al carro sin ingresar al mismo 
> necesito solucionar esto

### SUP-25

**Assunto:** dudas
- ID no banco: `25`; loja: **LAB Cafe** (`959276`).
- Status no banco: `OPEN`; plano na abertura: `PREMIUM_PLUS`; plano atual cadastrado: `PREMIUM_PLUS`.
- Desinstalação: Não informado.
- Criado: 03/11/2026 13:53:22; atualizado: 03/11/2026 16:00:46; última mensagem: 03/11/2026 16:00:46.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Conversa:**

**Mensagem 61 · STORE · 03/11/2026 13:53:22**

> 1. que sucede si yo cargo la orden de compra y se la mando al cliente?
> 2. no sirve para compra rapida en ese caso si no ingresa al producto no puede seleccionar los campos

**Mensagem 63 · SUPPORT · 03/11/2026 16:00:46**

> 1. Si creás el pedido manualmente y le enviás al cliente el enlace de pago, ese enlace no permite completar los campos de personalización de la app. Para que el cliente seleccione las opciones y queden asociadas al producto, enviale el enlace del producto antes de realizar la compra.
> 
> 2. Tenés razón: al agregar directamente desde la compra rápida, el cliente no pasa por los campos. Ya desarrollamos una mejora para que los productos con personalización dirijan al cliente a la página del producto. Está pendiente de publicación y validación; mientras tanto, es necesario ingresar al producto para completar las opciones. Los productos sin campos podrán mantener la compra rápida habitual.

### SUP-20

**Assunto:** Dudas sobre carga de archivos
- ID no banco: `20`; loja: **Atesora** (`7877166`).
- Status no banco: `OPEN`; plano na abertura: `FREE`; plano atual cadastrado: `FREE`.
- Desinstalação: 03/11/2026 10:09:19.
- Criado: 23/10/2026 00:01:46; atualizado: 28/10/2026 12:02:44; última mensagem: 28/10/2026 12:02:44.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Conversa:**

**Mensagem 48 · STORE · 23/10/2026 00:01:46**

> Hola quiero saber si tienes alguna función que asocia la cantidad del carrito con el campo de subir archivos, me refiero a que si en el carrito ponen 2, que puedan subir 2 fotos, si en el carro ponen 5 que puedan subir 5 fotos y así sucesivamente

**Mensagem 54 · SUPPORT · 28/10/2026 12:02:44**

> ¡Hola! Por el momento no tenemos esa función. Estamos planificando la opción de subir imágenes. ¿La idea sería que el cliente suba una foto diferente por cada unidad del mismo producto? Por ejemplo, si compra 5 unidades, que pueda subir 5 fotos distintas.

### SUP-18

**Assunto:** Duda sobre kits
- ID no banco: `18`; loja: **Kalimera Almacén de Café** (`8180542`).
- Status no banco: `OPEN`; plano na abertura: `FREE`; plano atual cadastrado: `PREMIUM_PLUS`.
- Desinstalação: Não informado.
- Criado: 20/10/2026 19:38:40; atualizado: 20/10/2026 19:53:36; última mensagem: 20/10/2026 19:53:36.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Conversa:**

**Mensagem 44 · STORE · 20/10/2026 19:38:40**

> Quiero que aparezca el campo personalizado en kits, pero veo que no me lo permite hacer

**Mensagem 45 · SUPPORT · 20/10/2026 19:53:36**

> ¡Hola! Gracias por escribirnos.
> 
> ¿Podés enviarnos el enlace de uno de los kits donde querés agregar el campo personalizado y contarnos si lo creaste desde Tiendanube o mediante otra aplicación?
> 
> También necesitamos saber si el kit no aparece en la lista de productos de nuestra app o si pudiste configurar el campo, pero no se muestra en la tienda.
> 
> Con esa información podemos revisar tu caso e indicarte cómo seguir.

## Solicitações — descrição integral

### REQ-5

**Título:** Interesse: Edição em massa
- ID no banco: `5`; loja: **Trio Ohana Personalizados** (`6361192`).
- Status no banco: `RECEIVED`; revisão: `0`.
- Plano no envio: `FREE`; plano atual cadastrado: `FREE`; desinstalação: Não informado.
- Criado: 12/11/2026 10:59:03.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Descrição:**

> Tenho interesse no recurso Edição em massa. Gostaria de saber quando estiver disponível.

**Histórico de atualizações:**

Sem atualizações registradas.

### REQ-4

**Título:** Interesse: Edição em massa
- ID no banco: `4`; loja: **Trio Ohana Personalizados** (`6361192`).
- Status no banco: `RECEIVED`; revisão: `0`.
- Plano no envio: `FREE`; plano atual cadastrado: `FREE`; desinstalação: Não informado.
- Criado: 11/11/2026 11:32:23.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Descrição:**

> Tenho interesse no recurso Edição em massa. Gostaria de saber quando estiver disponível.

**Histórico de atualizações:**

Sem atualizações registradas.

### REQ-2

**Título:** campos personalizados
- ID no banco: `2`; loja: **Nili Papelaria** (`6014333`).
- Status no banco: `RECEIVED`; revisão: `0`.
- Plano no envio: `PREMIUM_PLUS`; plano atual cadastrado: `FREE`; desinstalação: 06/11/2026 09:51:49.
- Criado: 06/11/2026 06:36:02.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Descrição:**

> Gostaria de indicar uma sugestão de melhoria aos campos personalizados, onde fosse possível indicar os valores das variações. Não sei se esta condição já está disponível, mas se este for o caso, que ficasse mais visível e fácil de encontrar.
> 
> Desde já agradeço!

**Histórico de atualizações:**

Sem atualizações registradas.

### REQ-1

**Título:** Extensão de Arquivo e Campos Personalizados
- ID no banco: `1`; loja: **Bless Etiquetas \| Etiquetas Personalizadas, Fitas e Papelaria para Sua Marca ** (`6552485`).
- Status no banco: `RECEIVED`; revisão: `0`.
- Plano no envio: `PREMIUM_PLUS`; plano atual cadastrado: `FREE`; desinstalação: 11/11/2026 18:57:20.
- Criado: 31/10/2026 16:50:37.
- Tratamento local: pendente de triagem.
- Decisão / próxima ação: a definir.

**Descrição:**

> Seria melhor criar os campos e depois escolher em quais produtos eles ficarão disponíveis. Pra mim, o campo dos produtos são os mesmos e tenho que preencher um por um no formato atual, o que não faz sentido.
> 
> Permitir que o cliente também envie arquivos na extensão pdf.

**Histórico de atualizações:**

Sem atualizações registradas.
