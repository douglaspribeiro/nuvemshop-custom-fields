package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.properties.SesEventsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import java.time.Duration;

@Configuration
public class SesEventsConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "notifications.ses-events.enabled", havingValue = "true")
    SqsClient sesEventsSqsClient(SesEventsProperties p) {
        if (!StringUtils.hasText(p.queueUrl()) || !StringUtils.hasText(p.region())
                || !StringUtils.hasText(p.configurationSet()))
            throw new IllegalStateException("Configure fila, região e Configuration Set dos eventos SES.");
        return SqsClient.builder().region(Region.of(p.region()))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(5))
                        .apiCallAttemptTimeout(Duration.ofSeconds(2))).build();
    }
}
