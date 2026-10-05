package com.manh.ecom_be.migrations;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.assertj.core.api.Assertions.*;

/** Narrow V8-column compatibility test; not a substitute for MySQL Flyway integration. */
class MoneyMigrationTest {
    @Test void wideningPreservesExistingValuesAndAllowsLargeExactAmounts() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:money-migration;MODE=MySQL");
             var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE products(id INT PRIMARY KEY, price DECIMAL(10,2))");
            sql.execute("CREATE TABLE orders(id INT PRIMARY KEY, total_money DECIMAL(12,2))");
            sql.execute("CREATE TABLE order_details(id INT PRIMARY KEY, price DECIMAL(10,2), total_money DECIMAL(10,2) DEFAULT 0)");
            sql.execute("INSERT INTO products VALUES(1, 99999999.99), (2, NULL)");
            sql.execute("INSERT INTO orders VALUES(1, 9999999999.99)");
            sql.execute("INSERT INTO order_details VALUES(1, 0.10, 0.30)");
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V9__standardize_money_precision.sql"));
            try (var result = sql.executeQuery("SELECT price FROM products WHERE id=1")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo("99999999.99");
                assertThat(result.getMetaData().getPrecision(1)).isEqualTo(19);
                assertThat(result.getMetaData().getScale(1)).isEqualTo(2);
            }
            try (var result = sql.executeQuery("SELECT price FROM products WHERE id=2")) {
                result.next();
                assertThat(result.getBigDecimal(1)).isNull();
            }
            try (var result = sql.executeQuery("SELECT total_money FROM orders WHERE id=1")) {
                result.next();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo("9999999999.99");
            }
            try (var result = sql.executeQuery("SELECT price, total_money FROM order_details WHERE id=1")) {
                result.next();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo("0.10");
                assertThat(result.getBigDecimal(2)).isEqualByComparingTo("0.30");
            }
            sql.execute("INSERT INTO order_details(id,price,total_money) VALUES(2,99999999999999999.99,99999999999999999.99)");
            try (var result = sql.executeQuery("SELECT total_money FROM order_details WHERE id=2")) {
                result.next();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo("99999999999999999.99");
            }
        }
    }
}
