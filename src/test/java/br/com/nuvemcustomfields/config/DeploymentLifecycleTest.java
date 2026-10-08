package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.controller.DeploymentLifecycleController;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

class DeploymentLifecycleTest {
    @Test void controlRejectsRemoteCallerAndInvalidState() {
        var controller = new DeploymentLifecycleController();
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        assertThat(controller.streams(request, "drain").getStatusCode().value()).isEqualTo(403);
        request.setRemoteAddr("127.0.0.1");
        assertThat(controller.streams(request, "invalid").getStatusCode().value()).isEqualTo(400);
        assertThat(controller.streams(request, "drain").getStatusCode().value()).isEqualTo(200);
    }
    @Test void shutdownWaitsForDetachedWork() throws Exception {
        var config = new DeploymentLifecycleConfiguration();
        var completed = new CountDownLatch(1);
        config.deploymentBackgroundExecutor().execute(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            completed.countDown();
        });
        config.shutdown();
        assertThat(completed.await(0, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(config.deploymentBackgroundExecutor().isTerminated()).isTrue();
        config.shutdown();
    }
}
