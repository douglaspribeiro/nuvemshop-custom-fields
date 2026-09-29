package br.com.nuvemcustomfields;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class MigrationVersionUniquenessTest {

    @Test
    void migrationVersionsAreUnique() throws Exception {
        var migrations = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*__*.sql");

        Map<String, Long> counts = Arrays.stream(migrations)
                .map(resource -> resource.getFilename().split("__", 2)[0])
                .collect(Collectors.groupingBy(version -> version, Collectors.counting()));

        assertThat(counts).allSatisfy((version, count) -> assertThat(count)
                .as("migration version %s", version)
                .isEqualTo(1));
    }
}
