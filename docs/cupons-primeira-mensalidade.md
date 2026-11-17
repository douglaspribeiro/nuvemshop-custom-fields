# Cupons na primeira mensalidade: Efí e Dodo

O cadastro fica em **Backoffice → Pagamentos → Cupons Efí e Dodo**. Escolha o
gateway, ambiente, finalidade **Primeira mensalidade**, plano, percentual, período
e limite total. Cada loja pode usar o benefício apenas no primeiro pagamento;
as renovações mantêm o valor integral. Descontos não se acumulam.

Os cupons existentes conservam gateway Efí e sua finalidade anterior. Um código
Efí não é aceito no checkout Dodo. No Dodo, os códigos precisam ter pelo menos
três caracteres e a finalidade é exclusivamente Primeira mensalidade.

## Cadastro e sincronização no Dodo

Ao salvar um cupom Dodo como ativo, o aplicativo:

1. Salva o cadastro local desativado e identifica os produtos validados do plano
   no catálogo do ambiente selecionado.
2. Consulta o código no Dodo. Se não existir, cria o desconto. Se já existir,
   só o atualiza quando os metadados comprovam que pertence ao mesmo cadastro
   do aplicativo. Não assume códigos de campanhas externas.
3. Define desconto percentual, `subscription_cycles=1`, restrição aos produtos do
   plano, datas, limite de utilizações e `preserve_on_plan_change=false`.
4. Valida a resposta e, só então, grava o identificador nativo e ativa o cupom.

A elegibilidade é por **loja Nuvemshop**, controlada no servidor, e não por e-mail
ou histórico global do pagador no Dodo: um responsável pode administrar várias
lojas. Por isso o desconto nativo usa `customer_eligibility=any`, sem teto por
cliente Dodo; o limite de uma utilização por loja fica no aplicativo.

Uma resposta incerta deixa o cadastro desativado. Salvar novamente consulta o
mesmo código e seus metadados, permitindo recuperar uma criação já aceita pelo
Dodo. Não são criados códigos substitutos automaticamente.

Sandbox e produção usam credenciais, endpoints, produtos e descontos separados.
A integração não muda `DODO_ENABLED`, `DODO_TEST_ENABLED`, roteamento ou listas
de homologação. O gateway e os produtos do ambiente precisam estar configurados
antes de ativar um cupom.

A exclusão de um cadastro Dodo desativa o uso no aplicativo e preserva o vínculo
nativo. Ela não apaga o desconto no Dodo nem cancela um checkout já emitido.
Código, finalidade, gateway e ambiente de um cadastro Dodo não podem ser trocados;
para outra campanha, crie um novo cadastro.

## Compra

1. O lojista escolhe o plano e vê uma tela de revisão antes de abrir o Dodo.
2. Digita e aplica o código. O aplicativo confere histórico da loja, plano,
   ambiente, validade e utilização, além da configuração real do desconto no Dodo.
3. A API de prévia do Dodo calcula o preço regional sem desconto e o preço com o
   código. O aplicativo confere produto e moeda, inclusive CLP sem centavos,
   e mostra a primeira mensalidade e o valor integral das próximas.
4. Ao continuar, os valores são revalidados e a utilização é reservada sob lock
   antes de criar a sessão remota. Uma disputa pela última utilização não permite
   duas reservas.
5. A sessão recebe somente o código validado em `discount_codes`. O campo livre
   de cupons do checkout hospedado continua desativado, evitando troca e acúmulo
   de códigos fora das regras do aplicativo.
6. O lojista informa os dados e confirma o pagamento no Dodo, que exibe o total
   com os impostos aplicáveis. Os valores da revisão são anteriores aos impostos.
7. A confirmação autenticada da cobrança registra o cupom como utilizado. O Dodo
   encerra o desconto após um ciclo; não há restauração de preço pelo worker Efí.

Consultar ou aplicar no resumo não consome utilização. Um checkout pendente com
cupom deve ser retomado; não se troca seu código por outra tentativa. Uma resposta
incerta conserva a reserva até a conciliação. Uma cobrança recusada isolada não
libera a vaga enquanto a assinatura ainda permitir pagamento. A reserva é liberada
quando uma leitura autenticada confirma assinatura terminal sem pagamento aprovado.

O retorno do navegador não libera acesso. Produto, referência, ambiente e pagamento
continuam sendo verificados no servidor. A mensalidade recorrente mantém seu valor
normal; o histórico de pagamentos usa o valor realmente recebido na cobrança.

## Reconquista por e-mail

O e-mail inicial de desinstalação pede o motivo e não oferece cupom. No fluxo Efí,
a emissão automática de `VOLTE50-...` ocorre depois da resposta, se elegível e
habilitada, antes de preparar o e-mail posterior com a oferta.

A reconquista Dodo também gera o cupom automaticamente após uma resposta elegível.
O código único por loja é gravado antes de acessar o gateway. O worker confirma
o desconto nativo de 50% por uma mensalidade, válido por 30 dias e restrito aos
produtos validados do país. Só depois dessa confirmação é permitido preparar
o e-mail com a oferta. A mensagem e os termos da página usam o idioma da loja.

Falhas mantêm o e-mail em PREPARED e agendam nova tentativa com espera crescente,
sempre reaproveitando o código original. Uma criação aceita pelo Dodo com resposta
perdida é recuperada consultando o código e seus metadados; não se cria outro cupom.
Opt-out, reinstalação, exclusão, bloqueio, histórico de pagamento e mudança de
ambiente são revalidados antes de enviar. A chamada ao Dodo não mantém o lock da loja.

Depois da reinstalação, o cupom confirmado é aplicado automaticamente no resumo
Dodo, ou pode ser digitado. Ele é exclusivo da mesma loja, não é cumulativo e vale
uma vez. A cobrança autenticada marca o uso e a conversão; as renovações seguem
o valor integral, controlado nativamente pelo limite de um ciclo.

A emissão continua dependendo de `WINBACK_DISCOUNT_ENABLED=true`. Produção exige
também `WINBACK_DISCOUNT_ALLOW_PRODUCTION=true`; a liberação de lojas e as
configurações de SMTP/SES e envio permanecem válidas. A integração não liga
essas configurações nem dispara campanhas antigas.

## Validação e publicação

As migrations `V88__dodo_first_payment_coupons.sql` e
`V89__dodo_winback_coupons.sql` adicionam gateway, vínculos nativos e confirmação
da reconquista antes do e-mail,
com padrão `EFI` para registros anteriores. Validar upgrade do schema MySQL e
compatibilidade Hibernate antes do deploy. Os testes locais simulam respostas HTTP
e confirmação do Dodo; uma cobrança real em sandbox e a conferência da renovação
são etapas externas separadas.

Referências oficiais:

- [Criar desconto](https://docs.dodopayments.com/api-reference/discounts/create-discount)
- [Consultar código](https://docs.dodopayments.com/api-reference/discounts/get-discount-by-code)
- [Prévia do checkout](https://docs.dodopayments.com/api-reference/checkout-sessions/preview)
