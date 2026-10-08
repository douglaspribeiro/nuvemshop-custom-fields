package br.com.nuvemcustomfields.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Drain scheduled and detached work before the DataSource closes. */
@Configuration(proxyBeanMethods = false)
public class DeploymentLifecycleConfiguration {
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean closing = new AtomicBoolean();
    @Autowired(required = false) private ScheduledAnnotationBeanPostProcessor scheduled;
    @Autowired(required = false) private ThreadPoolTaskScheduler scheduler;
    @Value("${app.background.shutdown-timeout-seconds:30}") private int timeout = 30;

    @Bean(destroyMethod = "shutdown")
    public ExecutorService deploymentBackgroundExecutor() { return workers; }

    @Bean
    public static ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler result = new ThreadPoolTaskScheduler();
        result.setPoolSize(4);
        result.setThreadNamePrefix("custom-fields-jobs-");
        result.setWaitForTasksToCompleteOnShutdown(true);
        result.setAwaitTerminationSeconds(30);
        result.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        result.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return result;
    }

    @EventListener(ContextClosedEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void shutdown() {
        if (!closing.compareAndSet(false, true)) return;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(1, timeout));
        boolean completed = true;
        int cancelled = 0;
        if (scheduled != null) scheduled.getScheduledTasks().forEach(task -> task.cancel(false));
        if (scheduler != null) {
            scheduler.setAwaitTerminationMillis(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
            scheduler.shutdown();
            if (!scheduler.getScheduledExecutor().isTerminated()) {
                completed = false;
                cancelled += scheduler.getScheduledExecutor().shutdownNow().size();
            }
        }
        workers.shutdown();
        try {
            if (!workers.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                completed = false;
                cancelled += workers.shutdownNow().size();
            }
        } catch (InterruptedException interrupted) {
            completed = false;
            cancelled += workers.shutdownNow().size();
            Thread.currentThread().interrupt();
        }
        System.err.println("background-shutdown status=" + (completed ? "completed" : "incomplete") + " cancelled=" + cancelled);
    }
}
