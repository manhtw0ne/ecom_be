-- Empty database bootstrap. Existing databases must be explicitly baselined at 0.
CREATE TABLE roles (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(255) NOT NULL);
INSERT INTO roles (id, name) VALUES (1, 'USER'), (2, 'ADMIN');
CREATE TABLE categories (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(50) NOT NULL);
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, fullname VARCHAR(100), phone_number VARCHAR(15),
    email VARCHAR(255), address VARCHAR(200), password VARCHAR(200) NOT NULL,
    is_active BIT DEFAULT 1, date_of_birth DATETIME, role_id BIGINT DEFAULT 1,
    created_at DATETIME, updated_at DATETIME,
    FOREIGN KEY (role_id) REFERENCES roles(id)
);
CREATE TABLE products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(350) NOT NULL, price DECIMAL(10,2),
    thumbnail VARCHAR(300), description TEXT, category_id BIGINT,
    created_at DATETIME, updated_at DATETIME,
    FOREIGN KEY (category_id) REFERENCES categories(id)
);
CREATE TABLE coupons (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, code VARCHAR(255) NOT NULL UNIQUE, active BIT NOT NULL
);
CREATE TABLE coupon_conditions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, coupon_id BIGINT NOT NULL,
    attribute VARCHAR(255) NOT NULL, operator VARCHAR(255) NOT NULL,
    value VARCHAR(255) NOT NULL, discount_amount DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (coupon_id) REFERENCES coupons(id)
);
CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT, fullname VARCHAR(100), email VARCHAR(100),
    phone_number VARCHAR(100) NOT NULL, address VARCHAR(100), note TEXT, order_date DATETIME,
    status VARCHAR(50), total_money DECIMAL(12,2), shipping_method VARCHAR(255),
    shipping_address VARCHAR(255), shipping_date DATE, tracking_number VARCHAR(255),
    payment_method VARCHAR(255), is_active BIT, vnp_txn_ref VARCHAR(255), coupon_id BIGINT,
    created_at DATETIME, updated_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES users(id), FOREIGN KEY (coupon_id) REFERENCES coupons(id)
);
CREATE TABLE order_details (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, order_id BIGINT, product_id BIGINT,
    price DECIMAL(10,2) NOT NULL, number_of_products INT NOT NULL, total_money DECIMAL(12,2) NOT NULL,
    color VARCHAR(255), coupon_id BIGINT,
    FOREIGN KEY (order_id) REFERENCES orders(id), FOREIGN KEY (product_id) REFERENCES products(id),
    FOREIGN KEY (coupon_id) REFERENCES coupons(id)
);
CREATE TABLE product_images (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, product_id BIGINT, image_url VARCHAR(300),
    FOREIGN KEY (product_id) REFERENCES products(id)
);
CREATE TABLE tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, token VARCHAR(255), token_type VARCHAR(50),
    expiration_date DATETIME, revoked BIT, expired BIT, user_id BIGINT,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE social_accounts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(50) NOT NULL, name VARCHAR(150), email VARCHAR(150)
);
-- These are created here with BIGINT FKs; historical V4/V5 keep their IF NOT EXISTS semantics.
CREATE TABLE comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, product_id BIGINT, user_id BIGINT, content VARCHAR(255),
    created_at DATETIME, updated_at DATETIME,
    FOREIGN KEY (product_id) REFERENCES products(id), FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE favorites (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, product_id BIGINT, user_id BIGINT,
    FOREIGN KEY (product_id) REFERENCES products(id), FOREIGN KEY (user_id) REFERENCES users(id)
);
