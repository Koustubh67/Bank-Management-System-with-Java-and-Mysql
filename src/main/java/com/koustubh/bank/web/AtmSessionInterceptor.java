package com.koustubh.bank.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/** ATM screens need a card "inserted" (PIN entered) in this session; otherwise go to the insert-card screen. */
public class AtmSessionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(AtmController.ATM_CARD) == null) {
            response.sendRedirect(request.getContextPath() + "/atm/login");
            return false;
        }
        return true;
    }
}
