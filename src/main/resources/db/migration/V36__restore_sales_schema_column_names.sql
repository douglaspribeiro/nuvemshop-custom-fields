-- V30 foi aplicado em uma versão anterior com nomes específicos de relatório.
-- O modelo atual utiliza os nomes originais; preservamos os dados ao renomear.
ALTER TABLE store_order_sales
    RENAME COLUMN personalized_product_value TO product_value;

ALTER TABLE store_sales_sync
    RENAME COLUMN personalized_value_backfilled TO product_value_backfilled;
