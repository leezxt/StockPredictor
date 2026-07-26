package org.gtalent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppMetaRepositoryTest {
    private AppMetaRepository repository;

    @BeforeEach
    void setUp() {
        String url = "jdbc:h2:mem:app-meta-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        DatabaseSchemaInitializer.initialize(() -> DriverManager.getConnection(url, "sa", ""));
        repository = new AppMetaRepository(() -> DriverManager.getConnection(url, "sa", ""));
    }

    @Test
    void returnsFalseForMissingOrInvalidKeys() {
        assertFalse(repository.isMetaFlagSet("missing"));
        assertFalse(repository.isMetaFlagSet(""));
        assertFalse(repository.isMetaFlagSet(null));
    }

    @Test
    void upsertsAndReadsBooleanFlag() {
        repository.setMetaFlag("initial-backup", "true");
        assertTrue(repository.isMetaFlagSet("initial-backup"));

        repository.setMetaFlag("initial-backup", "false");
        assertFalse(repository.isMetaFlagSet("initial-backup"));
    }
}
