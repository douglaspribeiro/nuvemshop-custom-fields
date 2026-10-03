# Planos e relatórios no backoffice

A tela `/backoffice/plans` mantém versões dos cinco planos atuais (Free, Grátis, Essencial, Pro e Ultra). Cada versão contém nome, descrição, preço/moeda de referência, limites e início de vigência. Os limites de produtos e campos usados no painel e na loja vêm da versão vigente; `-1` significa ilimitado.

A migration V45 preserva os limites atuais: Free 1/1, Grátis 1/3, Essencial 10/3, Pro 50/ilimitado e Ultra ilimitado/ilimitado. Os preços de referência iniciais são R$ 19,99, R$ 29,99 e R$ 59,90 para os planos pagos.

Uma nova versão encerra a anterior no dia precedente. Substituições no mesmo dia preservam a versão anterior no histórico, marcando-a como inativa. Quando há uma versão futura agendada, a próxima versão deve começar nessa data ou depois dela; uma versão não pode deixar um período sem cobertura. As escritas do mesmo plano são serializadas no banco.

Nome e preço de referência pertencem ao catálogo do backoffice; os nomes apresentados na interface do lojista continuam vindo da identidade comercial atual. O preço de referência é usado apenas na estimativa de receita de lojas sem valor contratado registrado e com moeda compatível. Criar uma versão não reajusta assinaturas nem altera os IDs externos dos gateways. Preços reais por país, moeda, provedor e ambiente continuam na tela Pagamentos. A configuração do billing nativo da Nuvemshop é preservada.

O relatório de receita usa o valor contratado nas assinaturas EFI/Paddle de produção ativas, com acesso ativo e sem cancelamento solicitado. Lojas desinstaladas, em cortesia, com cobrança suspensa ou plano gratuito não compõem a receita. Uma assinatura pendente, cancelada ou de teste não recebe um valor estimado alternativo.

Valores são separados por moeda, sem conversão cambial. Para reconquista com desconto inicial, o MRR usa o valor mensal regular e a próxima cobrança usa o valor vigente registrado na assinatura. Para lojas sem assinatura EFI/Paddle, são usados o valor e a próxima execução registrados no billing nativo; a ausência de valor permite usar o preço de referência compatível do plano.

A projeção cobre as próximas cobranças de hoje ao final do mês, pelo fuso de São Paulo. Não inclui vencidos, pagamentos já realizados, reajustes de upgrade ou uma previsão de recebimentos confirmados. Assinaturas sem próxima cobrança conhecida compõem o MRR, mas não a projeção.

A branch `plans-in-bd` foi preservada. A integração Shopify e a troca de JPA por JDBC não foram incorporadas.
