ALTER TABLE order_details ADD COLUMN product_name_snapshot VARCHAR(350) NULL;
ALTER TABLE order_details ADD COLUMN product_thumbnail_snapshot VARCHAR(300) NULL;

-- Best available legacy values at migration time, not reconstructed purchase history.
-- Query the table directly so soft-deleted products are included.
UPDATE order_details
SET product_name_snapshot = (SELECT p.name FROM products p WHERE p.id = order_details.product_id),
    product_thumbnail_snapshot = (SELECT p.thumbnail FROM products p WHERE p.id = order_details.product_id);
