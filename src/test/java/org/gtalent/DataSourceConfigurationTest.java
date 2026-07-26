package org.gtalent;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:spring-datasource;DB_CLOSE_DELAY=-1",
                "spring.task.scheduling.enabled=false",
                "app.browser.auto-open=false"
        })
class DataSourceConfigurationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void springOwnsHikariPoolAndInitializesSchema() throws Exception {
        assertTrue(dataSource instanceof HikariDataSource);

        try (Connection springConnection = dataSource.getConnection()) {
            try (ResultSet tables = springConnection.getMetaData()
                    .getTables(null, null, "STOCK_DATA", new String[]{"TABLE"})) {
                assertTrue(tables.next());
            }
            try (ResultSet migration = springConnection.createStatement().executeQuery(
                    "SELECT \"version\", \"success\" FROM \"flyway_schema_history\" " +
                            "WHERE \"version\" = '1' AND \"success\" = TRUE")) {
                assertTrue(migration.next());
            }
        }
    }
}
