package com.koustubh.bank.web;

import com.koustubh.bank.service.AdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private final AdminService admin;

    public HomeController(AdminService admin) {
        this.admin = admin;
    }

    /** The home page shows live numbers from the database, not made-up marketing figures. */
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("stats", admin.dashboard());
        return "index";
    }

    /** One login page with two choices: customer (ATM or UPI) or bank staff. */
    @GetMapping("/login")
    public String login(@RequestParam(required = false) String as, Model model) {
        model.addAttribute("staff", "staff".equals(as));
        return "login";
    }
}
