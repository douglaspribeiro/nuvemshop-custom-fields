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

    private final AdminSessionInterceptor adminSessionInterceptor;
    private final BackofficeSessionInterceptor backofficeSessionInterceptor;

    public WebConfig(AdminSessionInterceptor adminSessionInterceptor, BackofficeSessionInterceptor backofficeSessionInterceptor) {
        this.adminSessionInterceptor = adminSessionInterceptor;
        this.backofficeSessionInterceptor = backofficeSessionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminSessionInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/embedded", "/admin/nexo/session");
        registry.addInterceptor(backofficeSessionInterceptor)
                .addPathPatterns("/backoffice/**")
                .excludePathPatterns("/backoffice/login");
    }

    /** O nome do bean precisa ser localeResolver: e por ele que o DispatcherServlet procura. */
    @Bean
    public LocaleResolver localeResolver() {
        return new AppLocaleResolver();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/public/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "HEAD", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(1800);
    }
}
