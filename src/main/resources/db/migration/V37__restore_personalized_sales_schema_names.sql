-- Compatibiliza bancos que receberam a V36 antes da consolidação do modelo
-- de vendas personalizadas. Apenas renomeia colunas, sem alterar valores.
ALTER TABLE store_order_sales
    RENAME COLUMN product_value TO personalized_product_value;

ALTER TABLE store_sales_sync
    RENAME COLUMN product_value_backfilled TO personalized_value_backfilled;
