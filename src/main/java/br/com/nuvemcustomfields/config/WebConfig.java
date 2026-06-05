package br.com.nuvemcustomfields.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminSessionInterceptor adminSessionInterceptor;
    private final BackofficeSessionInterceptor backofficeSessionInterceptor;
    private final ShopifySessionInterceptor shopifySessionInterceptor;

    public WebConfig(
            AdminSessionInterceptor adminSessionInterceptor,
            BackofficeSessionInterceptor backofficeSessionInterceptor,
            ShopifySessionInterceptor shopifySessionInterceptor
    ) {
        this.adminSessionInterceptor = adminSessionInterceptor;
        this.backofficeSessionInterceptor = backofficeSessionInterceptor;
        this.shopifySessionInterceptor = shopifySessionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminSessionInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/embedded", "/admin/nexo/session");
        registry.addInterceptor(backofficeSessionInterceptor)
                .addPathPatterns("/backoffice/**")
                .excludePathPatterns("/backoffice/login");
        registry.addInterceptor(shopifySessionInterceptor)
                .addPathPatterns("/shopify/admin/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/public/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "HEAD", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(1800);
        registry.addMapping("/shopify/public/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "HEAD", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(1800);
    }
}
