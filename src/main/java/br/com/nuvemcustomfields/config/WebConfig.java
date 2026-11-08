package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.i18n.AppLocaleResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final HomologAccessInterceptor homologAccess;
    private final UserJourneyAuditInterceptor journeyAudit;

    private final AdminSessionInterceptor adminSessionInterceptor;
    private final BackofficeSessionInterceptor backofficeSessionInterceptor;

    public WebConfig(AdminSessionInterceptor adminSessionInterceptor, BackofficeSessionInterceptor backofficeSessionInterceptor, UserJourneyAuditInterceptor journeyAudit, HomologAccessInterceptor homologAccess) {
        this.homologAccess = homologAccess;
        this.journeyAudit = journeyAudit;
        this.adminSessionInterceptor = adminSessionInterceptor;
        this.backofficeSessionInterceptor = backofficeSessionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(journeyAudit)
                .addPathPatterns("/admin/**", "/oauth/callback", "/support", "/support/", "/support/tickets/**");
        registry.addInterceptor(adminSessionInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/embedded", "/admin/nexo/session");
        registry.addInterceptor(homologAccess)
                .addPathPatterns("/admin/**", "/public/stores/*/buyer-uploads/**");
        registry.addInterceptor(backofficeSessionInterceptor)
                .addPathPatterns("/backoffice/**")
                .excludePathPatterns("/backoffice/login");
    }

    @Override
    public void configureAsyncSupport(org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer configurer) {
        configurer.registerCallableInterceptors(new org.springframework.web.context.request.async.CallableProcessingInterceptor() {
            @Override public <T> void beforeConcurrentHandling(org.springframework.web.context.request.NativeWebRequest request,java.util.concurrent.Callable<T> task) {
                var servlet=request.getNativeRequest(jakarta.servlet.http.HttpServletRequest.class);
                if(servlet!=null && servlet.getRequestURI().equals(servlet.getContextPath()+"/admin/buyer-uploads/download-zip")) {
                    var async=org.springframework.web.context.request.async.WebAsyncUtils.getAsyncManager(servlet).getAsyncWebRequest();
                    if(async!=null)async.setTimeout(java.time.Duration.ofMinutes(10).toMillis());
                }
            }
        });
    }

    /** O nome do bean precisa ser localeResolver: e por ele que o DispatcherServlet procura. */
    @Bean
    public LocaleResolver localeResolver() {
        return new AppLocaleResolver();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/public/stores/*/buyer-uploads/**")
                .allowedOriginPatterns("*").allowedMethods("GET","POST","DELETE","OPTIONS")
                .allowedHeaders("Content-Type","X-Upload-Session","X-Upload-Request").maxAge(1800);
        registry.addMapping("/public/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "HEAD", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(1800);
    }
}
