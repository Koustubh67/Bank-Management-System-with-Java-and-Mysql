package com.koustubh.bank.web;

import com.koustubh.bank.domain.StaffRole;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.service.StaffService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/** Staff accounts (branch manager only) and every staff member's own password. */
@Controller
@RequestMapping("/admin")
public class StaffController {

    private final StaffService staff;

    public StaffController(StaffService staff) {
        this.staff = staff;
    }

    @GetMapping("/staff")
    public String list(Model model) {
        model.addAttribute("staff", staff.all());
        model.addAttribute("roles", StaffRole.values());
        return "admin/staff";
    }

    @PostMapping("/staff")
    public String create(@RequestParam(required = false) String username, @RequestParam(required = false) String fullName,
                         @RequestParam(required = false) StaffRole role, RedirectAttributes redirect) {
        try {
            StaffService.Created created = staff.create(username, fullName, role);
            redirect.addFlashAttribute("newStaff", created.user().getUsername());
            redirect.addFlashAttribute("tempPassword", created.temporaryPassword());
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/staff";
    }

    @PostMapping("/staff/{id}/reset")
    public String reset(@PathVariable Long id, Principal me, RedirectAttributes redirect) {
        try {
            String temporary = staff.resetPassword(id, me.getName());
            redirect.addFlashAttribute("tempPassword", temporary);
            redirect.addFlashAttribute("message", "Password reset. Share the temporary password in person");
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/staff";
    }

    @PostMapping("/staff/{id}/active")
    public String setActive(@PathVariable Long id, @RequestParam boolean active, Principal me, RedirectAttributes redirect) {
        try {
            staff.setActive(id, active, me.getName());
            redirect.addFlashAttribute("message", active ? "Login enabled" : "Login disabled");
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/staff";
    }

    /** Every staff member's own password. New staff are sent here until they replace their temporary password. */
    @GetMapping("/password")
    public String passwordForm(Principal me, Model model) {
        model.addAttribute("me", staff.get(me.getName()));
        return "admin/password";
    }

    @PostMapping("/password")
    public String changePassword(@RequestParam String currentPassword, @RequestParam String newPassword,
                                 @RequestParam String confirmPassword, Principal me, RedirectAttributes redirect) {
        try {
            if (!newPassword.equals(confirmPassword)) {
                throw new InvalidRequestException("New passwords do not match");
            }
            staff.changeOwnPassword(me.getName(), currentPassword, newPassword);
            redirect.addFlashAttribute("message", "Password changed");
            return "redirect:/admin";
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/password";
        }
    }
}
