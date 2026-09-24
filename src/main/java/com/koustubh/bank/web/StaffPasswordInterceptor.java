package com.koustubh.bank.web;

import com.koustubh.bank.repository.AdminUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/** Staff with a temporary password can't use the admin panel until they have chosen their own password. */
public class StaffPasswordInterceptor implements HandlerInterceptor {

    private final AdminUserRepository staff;

    public StaffPasswordInterceptor(AdminUserRepository staff) {
        this.staff = staff;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && staff.findByUsername(auth.getName()).map(u -> u.isMustChangePassword()).orElse(false)) {
            response.sendRedirect(request.getContextPath() + "/admin/password");
            return false;
        }
        return true;
    }
}
