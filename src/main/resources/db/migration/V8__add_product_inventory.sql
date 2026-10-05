ALTER TABLE products ADD COLUMN stock_quantity INT NOT NULL DEFAULT 0;
ALTER TABLE products ADD CONSTRAINT chk_product_stock_nonnegative CHECK (stock_quantity >= 0);
