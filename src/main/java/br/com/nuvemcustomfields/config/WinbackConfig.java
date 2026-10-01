package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.properties.WinbackProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import java.time.Duration;

@Configuration
public class WinbackConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "winback.enabled", havingValue = "true")
    SqsClient winbackSqsClient(WinbackProperties properties) {
        return SqsClient.builder().region(Region.of(properties.region()))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(5))
                        .apiCallAttemptTimeout(Duration.ofSeconds(2)))
                .build();
    }
}
