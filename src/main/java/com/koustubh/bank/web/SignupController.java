package com.koustubh.bank.web;

import com.koustubh.bank.domain.AccountType;
import com.koustubh.bank.dto.SignupForm;
import com.koustubh.bank.dto.SignupOptions;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.service.AccountOpeningService;
import com.koustubh.bank.service.OpenedAccount;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The 3-page account opening form. The form object lives in the session until the account is created. */
@Controller
@RequestMapping("/signup")
@SessionAttributes("signupForm")
public class SignupController {

    private final AccountOpeningService accountOpening;

    public SignupController(AccountOpeningService accountOpening) {
        this.accountOpening = accountOpening;
    }

    @ModelAttribute("signupForm")
    public SignupForm signupForm() {
        return new SignupForm();
    }

    @ModelAttribute
    public void options(Model model) {
        model.addAttribute("genders", SignupOptions.GENDERS);
        model.addAttribute("maritalStatuses", SignupOptions.MARITAL_STATUSES);
        model.addAttribute("religions", SignupOptions.RELIGIONS);
        model.addAttribute("categories", SignupOptions.CATEGORIES);
        model.addAttribute("incomes", SignupOptions.INCOMES);
        model.addAttribute("educations", SignupOptions.EDUCATION);
        model.addAttribute("occupations", SignupOptions.OCCUPATIONS);
        model.addAttribute("services", SignupOptions.SERVICES);
        model.addAttribute("accountTypes", AccountType.values());
    }

    /** Progress is tracked on the server; the browser must not be able to skip pages. */
    @InitBinder("signupForm")
    public void initBinder(WebDataBinder binder) {
        binder.setDisallowedFields("completedStep");
    }

    @GetMapping
    public String start() {
        return "redirect:/signup/personal";
    }

    @GetMapping("/personal")
    public String personal() {
        return "signup/personal";
    }

    @PostMapping("/personal")
    public String savePersonal(@Validated(SignupForm.Personal.class) @ModelAttribute("signupForm") SignupForm form,
                               BindingResult result) {
        if (result.hasErrors()) {
            return "signup/personal";
        }
        form.setCompletedStep(Math.max(form.getCompletedStep(), 1));
        return "redirect:/signup/additional";
    }

    @GetMapping("/additional")
    public String additional(@ModelAttribute("signupForm") SignupForm form) {
        return form.getCompletedStep() < 1 ? "redirect:/signup/personal" : "signup/additional";
    }

    @PostMapping("/additional")
    public String saveAdditional(@Validated(SignupForm.Additional.class) @ModelAttribute("signupForm") SignupForm form,
                                 BindingResult result) {
        if (form.getCompletedStep() < 1) {
            return "redirect:/signup/personal";
        }
        if (result.hasErrors()) {
            return "signup/additional";
        }
        form.setCompletedStep(Math.max(form.getCompletedStep(), 2));
        return "redirect:/signup/account";
    }

    @GetMapping("/account")
    public String account(@ModelAttribute("signupForm") SignupForm form) {
        return form.getCompletedStep() < 2 ? "redirect:/signup/additional" : "signup/account";
    }

    @PostMapping("/account")
    public String openAccount(@Validated(SignupForm.AccountDetails.class) @ModelAttribute("signupForm") SignupForm form,
                              BindingResult result, Model model, SessionStatus sessionStatus,
                              RedirectAttributes redirect) {
        if (form.getCompletedStep() < 2) {
            return "redirect:/signup/additional";
        }
        if (result.hasErrors()) {
            return "signup/account";
        }
        try {
            OpenedAccount opened = accountOpening.open(form);
            sessionStatus.setComplete();
            redirect.addFlashAttribute("opened", opened);
            redirect.addFlashAttribute("customerName", form.getFullName());
            return "redirect:/signup/done";
        } catch (BankException e) {
            model.addAttribute("error", e.getMessage());
            return "signup/account";
        }
    }

    @GetMapping("/done")
    public String done(Model model) {
        // The card number and PIN are only available once, straight after opening.
        return model.containsAttribute("opened") ? "signup/done" : "redirect:/";
    }
}
