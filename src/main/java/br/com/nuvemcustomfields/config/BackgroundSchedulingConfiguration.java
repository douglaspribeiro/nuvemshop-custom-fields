package br.com.nuvemcustomfields.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Keeps all web service beans available while only A runs scheduled work. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.background.enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundSchedulingConfiguration {
}
