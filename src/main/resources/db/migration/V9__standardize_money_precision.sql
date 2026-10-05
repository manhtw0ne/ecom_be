-- V0/V1 already use DECIMAL, but with different precision. Widen without
-- changing existing values, nullability or defaults. Do not rewrite old migrations.
ALTER TABLE products MODIFY COLUMN price DECIMAL(19,2) DEFAULT NULL;
ALTER TABLE orders MODIFY COLUMN total_money DECIMAL(19,2) DEFAULT NULL;
ALTER TABLE order_details MODIFY COLUMN price DECIMAL(19,2) DEFAULT NULL;
ALTER TABLE order_details MODIFY COLUMN total_money DECIMAL(19,2) DEFAULT 0;
