package br.com.nuvemcustomfields.migration;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationHistoryIntegrityTest {
    @Test void previouslyVersionedMigrationsMustNotBeChangedOrRemoved() throws Exception {
        var expected = new Properties();
        try (var manifest = getClass().getResourceAsStream("/migration-history-sha256.properties")) {
            assertThat(manifest).isNotNull();
            expected.load(manifest);
        }
        assertThat(expected).isNotEmpty();
        for (String file : expected.stringPropertyNames()) {
            try (var sql = getClass().getResourceAsStream("/db/migration/" + file)) {
                assertThat(sql).as("Existing Flyway migration %s must not be removed", file).isNotNull();
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sql.readAllBytes()));
                assertThat(actual).as("Do not edit %s: add a new versioned migration instead", file)
                        .isEqualTo(expected.getProperty(file));
            }
        }
    }
}
