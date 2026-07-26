package org.gtalent;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseUpsertSqlTest {

    @Test
    void buildsH2MergeUsingDeclaredKeys() throws Exception {
        Connection connection = connectionFor("H2");

        String sql = DatabaseUpsertSql.build(
                connection,
                "STOCK_DATA",
                new String[]{"symbol", "trade_date", "close_price"},
                new String[]{"symbol", "trade_date"});

        assertEquals(
                "MERGE INTO STOCK_DATA (symbol, trade_date, close_price) " +
                        "KEY (symbol, trade_date) VALUES (?, ?, ?)",
                sql);
    }

    @Test
    void buildsPostgresqlOnConflictAndUpdatesNonKeyColumns() throws Exception {
        Connection connection = connectionFor("PostgreSQL");

        String sql = DatabaseUpsertSql.build(
                connection,
                "STOCK_DATA",
                new String[]{"symbol", "trade_date", "close_price", "volume"},
                new String[]{"symbol", "trade_date"});

        assertEquals(
                "INSERT INTO STOCK_DATA (symbol, trade_date, close_price, volume) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT (symbol, trade_date) DO UPDATE SET " +
                        "close_price = EXCLUDED.close_price, volume = EXCLUDED.volume",
                sql);
    }

    @Test
    void rejectsUnknownDatabases() throws Exception {
        assertThrows(SQLException.class,
                () -> DatabaseUpsertSql.build(
                        connectionFor("UnknownDB"),
                        "APP_META",
                        new String[]{"meta_key", "meta_value"},
                        new String[]{"meta_key"}));
    }

    private Connection connectionFor(String productName) throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn(productName);
        return connection;
    }
}
