# dLocal Go removida

A integração deixou de fazer parte da aplicação. Checkout, upgrades, reembolsos, webhooks, conciliação e configurações dLocal Go foram removidos.

As migrações V61/V62, tabelas históricas e o identificador `DLOCAL_GO` foram preservados para leitura dos registros existentes. O backoffice não permite ativar rotas ou modificar preços desse provedor.

Esta remoção local não cancela contratos, planos ou cobranças existentes no provedor. Contratos ativos devem ser tratados diretamente no painel dLocal Go antes da retirada em produção. As rotas de país que usavam dLocal Go precisam ser configuradas com um gateway mantido e seu catálogo validado.

Continuam disponíveis Creem, Mercado Pago, Dodo, Efí e Stripe, respeitando as regras de país e catálogo de cada integração.
