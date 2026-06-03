package br.com.nuvemcustomfields.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminSessionInterceptor adminSessionInterceptor;

    public WebConfig(AdminSessionInterceptor adminSessionInterceptor) {
        this.adminSessionInterceptor = adminSessionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminSessionInterceptor)
                .addPathPatterns("/admin/**");
    }
}
