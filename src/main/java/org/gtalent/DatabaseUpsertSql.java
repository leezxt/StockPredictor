package org.gtalent;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class DatabaseUpsertSql {

    private DatabaseUpsertSql() {
    }

    static String build(Connection connection, String table, String[] columns, String[] keyColumns)
            throws SQLException {
        validateIdentifier(table);
        Arrays.stream(columns).forEach(DatabaseUpsertSql::validateIdentifier);
        Arrays.stream(keyColumns).forEach(DatabaseUpsertSql::validateIdentifier);

        String columnList = String.join(", ", columns);
        String placeholders = String.join(", ", java.util.Collections.nCopies(columns.length, "?"));
        String keyList = String.join(", ", keyColumns);
        if (isPostgreSql(connection)) {
            Set<String> keys = Arrays.stream(keyColumns)
                    .map(column -> column.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            List<String> updates = Arrays.stream(columns)
                    .filter(column -> !keys.contains(column.toLowerCase(Locale.ROOT)))
                    .map(column -> column + " = EXCLUDED." + column)
                    .toList();
            String conflictAction = updates.isEmpty()
                    ? "DO NOTHING"
                    : "DO UPDATE SET " + String.join(", ", updates);
            return "INSERT INTO " + table + " (" + columnList + ") VALUES (" + placeholders + ") " +
                    "ON CONFLICT (" + keyList + ") " + conflictAction;
        }
        return "MERGE INTO " + table + " (" + columnList + ") KEY (" + keyList + ") VALUES (" +
                placeholders + ")";
    }

    private static boolean isPostgreSql(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName();
        if (productName != null && productName.toLowerCase(Locale.ROOT).contains("postgresql")) {
            return true;
        }
        if (productName != null && productName.equalsIgnoreCase("H2")) {
            return false;
        }
        throw new SQLException("不支援的資料庫: " + productName);
    }

    private static void validateIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("不合法的 SQL identifier: " + identifier);
        }
    }
}
