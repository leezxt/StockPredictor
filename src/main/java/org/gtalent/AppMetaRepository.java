package org.gtalent;

import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class AppMetaRepository {

    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    private final ConnectionProvider connectionProvider;

    @Autowired
    public AppMetaRepository(DataSource dataSource) {
        this(dataSource::getConnection);
    }

    AppMetaRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public boolean isMetaFlagSet(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String sql = "SELECT meta_value FROM APP_META WHERE meta_key = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && "true".equals(rs.getString("meta_value"));
            }
        } catch (SQLException e) {
            System.err.println("查詢 APP_META 失敗: " + e.getMessage());
            return false;
        }
    }

    public void setMetaFlag(String key, String value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DatabaseUpsertSql.build(
                     conn,
                     "APP_META",
                     new String[]{"meta_key", "meta_value", "updated_at"},
                     new String[]{"meta_key"}))) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.setTimestamp(3, Timestamp.from(Instant.now()));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("寫入 APP_META 失敗: " + e.getMessage());
        }
    }
}
