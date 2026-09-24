package com.koustubh.bank.web;

import com.koustubh.bank.domain.AccountType;
import com.koustubh.bank.dto.SignupForm;
import com.koustubh.bank.dto.SignupOptions;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.service.AccountOpeningService;
import com.koustubh.bank.service.KycFiles;
import com.koustubh.bank.service.OpenedAccount;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.multipart.MultipartFile;
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
        binder.setDisallowedFields("completedStep", "panDocument", "aadhaarDocument");
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
        // Already-registered details are reported on this page, together with every other error
        if (!result.hasFieldErrors("mobile")) {
            accountOpening.duplicatesOnPersonalPage(form.getMobile())
                    .forEach((field, message) -> result.rejectValue(field, "duplicate", message));
        }
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
                                 BindingResult result,
                                 @RequestParam(required = false) MultipartFile panFile,
                                 @RequestParam(required = false) MultipartFile aadhaarFile) {
        if (form.getCompletedStep() < 1) {
            return "redirect:/signup/personal";
        }
        // A new file replaces the one kept in the session; going back without choosing a file keeps the old one.
        try {
            if (hasFile(panFile) || form.getPanDocument() == null) {
                form.setPanDocument(KycFiles.accept(panFile, "PAN card"));
            }
        } catch (BankException e) {
            result.rejectValue("panDocument", "upload", e.getMessage());
        }
        try {
            if (hasFile(aadhaarFile) || form.getAadhaarDocument() == null) {
                form.setAadhaarDocument(KycFiles.accept(aadhaarFile, "Aadhaar card"));
            }
        } catch (BankException e) {
            result.rejectValue("aadhaarDocument", "upload", e.getMessage());
        }
        accountOpening.duplicatesOnKycPage(result.hasFieldErrors("pan") ? null : form.getPan(),
                        result.hasFieldErrors("aadhaar") ? null : form.getAadhaar())
                .forEach((field, message) -> result.rejectValue(field, "duplicate", message));
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

    @GetMapping("/status")
    public String statusForm() {
        return "signup/status";
    }

    @PostMapping("/status")
    public String status(@RequestParam(required = false) String accountNumber, @RequestParam(required = false) String pan,
                         Model model) {
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("pan", pan);
        try {
            model.addAttribute("tracked", accountOpening.status(accountNumber, pan));
        } catch (BankException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "signup/status";
    }

    private static boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    @GetMapping("/done")
    public String done(Model model) {
        // The card number and PIN are only available once, straight after opening.
        return model.containsAttribute("opened") ? "signup/done" : "redirect:/";
    }
}
