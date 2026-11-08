package br.com.nuvemcustomfields.properties;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class HomologEnvironmentTest {
    @Configuration(proxyBeanMethods=false)
    @EnableConfigurationProperties({HomologProperties.class, DodoTestProperties.class, StripeProperties.class, WinbackProperties.class})
    static class Settings {}

    @ParameterizedTest @ValueSource(strings={"0", "*", "42,99"})
    void bindsUniversalEnvironmentAndIgnoresOldProviderLists(String ids) {
        new ApplicationContextRunner().withUserConfiguration(Settings.class).withInitializer(context -> {
            try {
                for (var source : new YamlPropertySourceLoader().load("application-main",
                        new FileSystemResource("src/main/resources/application.yml")))
                    context.getEnvironment().getPropertySources().addLast(source);
            } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
            context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource("test-env",Map.of(
                    "HOMOLOG_ALLOWED_STORE_IDS",ids,
                    "STRIPE_ALLOWED_STORE_IDS","123",
                    "DODO_TEST_ALLOWED_STORE_IDS","123",
                    "WINBACK_ALLOWED_STORE_IDS","123")));
        }).run(context -> {
            assertThat(context).hasNotFailed();
            var policy=context.getBean(HomologProperties.class);
            assertThat(policy.allowedStoreIds()).isEqualTo(ids);
            assertThat(policy.allows(42L)).isEqualTo(!ids.equals("0"));
            assertThat(policy.allows(123L)).isEqualTo(ids.equals("*"));
        });
    }
}
