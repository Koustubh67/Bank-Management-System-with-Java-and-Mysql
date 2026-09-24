package com.koustubh.bank.web;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Creates the CSRF token (and its session) before a page starts rendering. Spring Security loads the token lazily;
 * on a large page (e.g. /loans) the server may already have started sending HTML when it reaches a form, and at
 * that point a session can no longer be created, so first-time visitors would get a broken page.
 */
@ControllerAdvice
public class CsrfTokenAdvice {

    @ModelAttribute
    public void loadCsrfToken(CsrfToken token) {
        if (token != null) {
            token.getToken();
        }
    }
}
