package com.manh.ecom_be.migrations;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class OrderSnapshotMigrationTest {
    @Test void backfillsActiveAndHiddenProductsWithoutChangingMoney() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:order-snapshot;MODE=MySQL");
             var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE products(id BIGINT PRIMARY KEY,name VARCHAR(350),thumbnail VARCHAR(300),is_deleted BOOLEAN)");
            sql.execute("CREATE TABLE order_details(id BIGINT PRIMARY KEY,product_id BIGINT,price DECIMAL(19,2))");
            sql.execute("INSERT INTO products VALUES(1,'Original','a.png',false),(2,'Hidden',null,true)");
            sql.execute("INSERT INTO order_details VALUES(1,1,12.34),(2,2,56.78)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V11__order_product_snapshots.sql"));
            sql.execute("UPDATE products SET name='Changed',thumbnail='new.png'");
            try (var rows = sql.executeQuery("SELECT * FROM order_details ORDER BY id")) {
                rows.next();
                assertThat(rows.getString("product_name_snapshot")).isEqualTo("Original");
                assertThat(rows.getString("product_thumbnail_snapshot")).isEqualTo("a.png");
                assertThat(rows.getBigDecimal("price")).isEqualByComparingTo("12.34");
                rows.next();
                assertThat(rows.getString("product_name_snapshot")).isEqualTo("Hidden");
                assertThat(rows.getString("product_thumbnail_snapshot")).isNull();
                assertThat(rows.getBigDecimal("price")).isEqualByComparingTo("56.78");
            }
        }
    }
}
