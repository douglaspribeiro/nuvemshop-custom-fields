package br.com.nuvemcustomfields.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class BackgroundSchedulingConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BackgroundSchedulingConfiguration.class, Jobs.class);

    @Test
    void schedulesByDefaultForExistingDeployments() {
        assertScheduling(runner);
    }

    @Test
    void schedulesOnA() {
        assertScheduling(runner.withPropertyValues("app.background.enabled=true"));
    }

    @Test
    void keepsWebServicesButDoesNotRegisterScheduledTasksOnB() {
        runner.withPropertyValues("app.background.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(WebServiceWithJob.class);
            assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
            assertThat(context.getBean(WebServiceWithJob.class).webResponse()).isEqualTo("ok");
        });
    }

    private void assertScheduling(ApplicationContextRunner contextRunner) {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(WebServiceWithJob.class);
            assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).hasSize(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class Jobs {
        @Bean WebServiceWithJob webServiceWithJob() { return new WebServiceWithJob(); }
    }

    static class WebServiceWithJob {
        String webResponse() { return "ok"; }
        @Scheduled(fixedDelay = 60000, initialDelay = 60000)
        public void scheduledWork() { }
    }
}
