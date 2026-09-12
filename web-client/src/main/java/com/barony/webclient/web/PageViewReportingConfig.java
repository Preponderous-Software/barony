package com.barony.webclient.web;

import com.barony.webclient.service.UsageReportingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link PageViewInterceptor} for every request. It is registered from a plain
 * {@code @Bean} rather than as a scanned {@code HandlerInterceptor} component so that
 * {@code @WebMvcTest} slices, which pick up interceptors but not services, keep working without
 * a stand-in for {@link UsageReportingService}.
 */
@Configuration
public class PageViewReportingConfig {

    @Bean
    public WebMvcConfigurer pageViewReporting(UsageReportingService usageReporting) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new PageViewInterceptor(usageReporting));
            }
        };
    }
}
