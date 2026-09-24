package com.koustubh.bank.config;

import com.koustubh.bank.repository.AdminUserRepository;
import com.koustubh.bank.web.AtmSessionInterceptor;
import com.koustubh.bank.web.StaffPasswordInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminUserRepository staff;

    public WebConfig(AdminUserRepository staff) {
        this.staff = staff;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AtmSessionInterceptor())
                .addPathPatterns("/atm/**")
                .excludePathPatterns("/atm/login", "/atm/insert");
        registry.addInterceptor(new StaffPasswordInterceptor(staff))
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/password", "/admin/login", "/admin/logout");
    }
}
