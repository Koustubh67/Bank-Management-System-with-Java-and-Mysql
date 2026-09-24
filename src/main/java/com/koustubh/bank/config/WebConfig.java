package com.koustubh.bank.config;

import com.koustubh.bank.web.AtmSessionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AtmSessionInterceptor())
                .addPathPatterns("/atm/**")
                .excludePathPatterns("/atm/login", "/atm/insert");
    }
}
