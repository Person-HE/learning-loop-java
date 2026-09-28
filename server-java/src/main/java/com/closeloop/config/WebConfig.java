package com.closeloop.config;

import com.closeloop.web.AccessLogInterceptor;
import com.closeloop.web.RolloverInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 前后端分离：开发模式 Vite(5173) 跨域直连；生产模式由 static/ 托管 client/dist */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RolloverInterceptor rolloverInterceptor;
    private final AccessLogInterceptor accessLogInterceptor;

    public WebConfig(RolloverInterceptor rolloverInterceptor, AccessLogInterceptor accessLogInterceptor) {
        this.rolloverInterceptor = rolloverInterceptor;
        this.accessLogInterceptor = accessLogInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accessLogInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(rolloverInterceptor).addPathPatterns("/api/**");
    }
}
