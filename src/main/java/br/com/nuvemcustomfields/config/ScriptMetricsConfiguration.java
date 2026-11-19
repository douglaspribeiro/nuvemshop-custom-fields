package br.com.nuvemcustomfields.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ScriptMetricsConfiguration {
    @Bean
    MeterFilter scriptVersionCardinalityLimit() {
        return MeterFilter.maximumAllowableTags(StorefrontTrafficMetricsFilter.SCRIPT_EXECUTION_METRIC,
                "script_version", 128, MeterFilter.deny());
    }
}
