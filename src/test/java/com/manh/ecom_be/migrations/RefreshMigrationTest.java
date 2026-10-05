package com.manh.ecom_be.migrations;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.assertj.core.api.Assertions.*;

class RefreshMigrationTest {
    @Test void migrationPreservesCredentialsAndNormalizesLegacyEmptyValues() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:refresh-migration;MODE=MySQL");
             var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE tokens(id INT PRIMARY KEY, token VARCHAR(255), refresh_token VARCHAR(255) DEFAULT '')");
            sql.execute("INSERT INTO tokens VALUES(1,'access','refresh'),(2,'old',''),(3,'old2','')");
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V10__refresh_token_rotation.sql"));
            try (var rows = sql.executeQuery("SELECT token,refresh_token FROM tokens WHERE id=1")) {
                rows.next();
                assertThat(rows.getString(1)).isEqualTo("access");
                assertThat(rows.getString(2)).isEqualTo("refresh");
                assertThat(rows.getMetaData().getPrecision(1)).isEqualTo(2048);
            }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM tokens WHERE refresh_token IS NULL")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
            assertThatThrownBy(() -> sql.execute("INSERT INTO tokens VALUES(4,'another','refresh')"))
                    .isInstanceOf(SQLException.class);
            sql.execute("INSERT INTO tokens(id,token) VALUES(5,'" + "x".repeat(512) + "')");
        }
    }
}