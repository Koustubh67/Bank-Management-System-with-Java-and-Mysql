package com.koustubh.bank.web;

import com.koustubh.bank.domain.Customer;
import com.koustubh.bank.domain.FdTenure;
import com.koustubh.bank.domain.Investment;
import com.koustubh.bank.domain.InvestmentType;
import com.koustubh.bank.domain.PaymentMethod;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.market.FundCatalog;
import com.koustubh.bank.market.MarketDataService;
import com.koustubh.bank.service.CustomerService;
import com.koustubh.bank.service.InvestmentService;
import com.koustubh.bank.service.InvestmentService.Order;
import com.koustubh.bank.service.NotificationService;
import com.koustubh.bank.service.UpiService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Investing inside net banking: explore real funds → choose SIP or one-time → confirm KYC (PAN, Aadhaar, mobile)
 * → pay by account (OTP) or UPI (UPI PIN) → confirmation. The order, KYC check and OTP live in the session.
 */
@Controller
@RequestMapping("/customer/invest")
public class InvestController {

    static final String ORDER = "INVEST_ORDER";
    static final String KYC_OK = "INVEST_KYC_OK";
    static final String OTP = "INVEST_OTP";
    static final String OTP_EXPIRES = "INVEST_OTP_EXPIRES";

    private final InvestmentService investments;
    private final MarketDataService market;
    private final CustomerService customers;
    private final UpiService upi;
    private final NotificationService notifications;
    private final SecureRandom random;

    public InvestController(InvestmentService investments, MarketDataService market, CustomerService customers,
                            UpiService upi, NotificationService notifications, SecureRandom random) {
        this.investments = investments;
        this.market = market;
        this.customers = customers;
        this.upi = upi;
        this.notifications = notifications;
        this.random = random;
    }

    @GetMapping
    public String home(Principal principal, Model model) {
        model.addAttribute("o", customers.overview(principal.getName()));
        model.addAttribute("portfolio", investments.portfolio(principal.getName()));
        model.addAttribute("funds", market.catalogue());
        model.addAttribute("tenures", FdTenure.values());
        return "customer/invest";
    }

    @GetMapping("/funds/{code}")
    public String fund(@PathVariable long code, Principal principal, Model model) {
        if (FundCatalog.find(code).isEmpty()) {
            return "redirect:/customer/invest";
        }
        model.addAttribute("o", customers.overview(principal.getName()));
        try {
            model.addAttribute("fund", market.snapshot(code));
        } catch (BankException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "customer/fund";
    }

    @GetMapping("/holdings/{id}")
    public String holding(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("o", customers.overview(principal.getName()));
        model.addAttribute("h", investments.holding(principal.getName(), id));
        return "customer/holding";
    }

    /** Step 1: what to buy. Validated, then kept in the session for checkout. */
    @PostMapping("/order")
    public String order(@RequestParam(required = false) InvestmentType type, @RequestParam(required = false) Long schemeCode,
                        @RequestParam(required = false) String amount, @RequestParam(required = false) FdTenure tenure,
                        HttpSession session, RedirectAttributes redirect) {
        try {
            Order order = investments.validate(new Order(type, schemeCode, parse(amount), tenure));
            session.setAttribute(ORDER, order);
            session.removeAttribute(KYC_OK);
            session.removeAttribute(OTP);
            return "redirect:/customer/invest/checkout";
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return schemeCode != null ? "redirect:/customer/invest/funds/" + schemeCode : "redirect:/customer/invest";
        }
    }

    @GetMapping("/checkout")
    public String checkout(HttpSession session, Principal principal, Model model) {
        Order order = (Order) session.getAttribute(ORDER);
        if (order == null) {
            return "redirect:/customer/invest";
        }
        CustomerService.Overview o = customers.overview(principal.getName());
        model.addAttribute("o", o);
        model.addAttribute("order", order);
        model.addAttribute("kycOk", Boolean.TRUE.equals(session.getAttribute(KYC_OK)));
        model.addAttribute("methods", PaymentMethod.values());
        model.addAttribute("vpa", upi.vpaForCustomer(principal.getName()).orElse(null));
        model.addAttribute("otpSent", session.getAttribute(OTP) != null);
        if (order.schemeCode() != null) {
            try {
                model.addAttribute("fund", market.snapshot(order.schemeCode()));
            } catch (BankException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        return "customer/checkout";
    }

    /** Step 2: confirm KYC. Every mismatch is reported at once. */
    @PostMapping("/checkout/kyc")
    public String kyc(@RequestParam(required = false) String pan, @RequestParam(required = false) String aadhaar,
                      @RequestParam(required = false) String mobile, HttpSession session, Principal principal,
                      RedirectAttributes redirect) {
        if (session.getAttribute(ORDER) == null) {
            return "redirect:/customer/invest";
        }
        Customer c = customers.overview(principal.getName()).customer();
        List<String> errors = new ArrayList<>();
        String cleanPan = pan == null ? "" : pan.replaceAll("\\s", "").toUpperCase();
        String cleanAadhaar = aadhaar == null ? "" : aadhaar.replaceAll("[\\s-]", "");
        String cleanMobile = mobile == null ? "" : mobile.replaceAll("[\\s-]", "").replaceFirst("^(\\+91|0)", "");
        if (!cleanPan.equals(c.getPan())) errors.add("PAN doesn't match our records");
        if (!cleanAadhaar.equals(c.getAadhaar())) errors.add("Aadhaar number doesn't match our records");
        if (c.getMobile() == null) errors.add("Add your mobile number in Profile first");
        else if (!cleanMobile.equals(c.getMobile())) errors.add("Mobile number doesn't match the one on your account");
        if (!errors.isEmpty()) {
            redirect.addFlashAttribute("kycErrors", errors);
            redirect.addFlashAttribute("kycPan", pan);
            redirect.addFlashAttribute("kycMobile", mobile);
            return "redirect:/customer/invest/checkout";
        }
        session.setAttribute(KYC_OK, true);
        return "redirect:/customer/invest/checkout#payment";
    }

    /** Sends a 6-digit OTP by (simulated) SMS for paying from the account. */
    @PostMapping("/checkout/otp")
    public String sendOtp(HttpSession session, Principal principal, RedirectAttributes redirect) {
        if (!Boolean.TRUE.equals(session.getAttribute(KYC_OK))) {
            return "redirect:/customer/invest/checkout";
        }
        String otp = String.format("%06d", random.nextInt(1_000_000));
        session.setAttribute(OTP, otp);
        session.setAttribute(OTP_EXPIRES, Instant.now().plusSeconds(300));
        notifications.sendOtp(customers.overview(principal.getName()).customer(), otp, "confirm your investment");
        redirect.addFlashAttribute("smsOtp", otp);
        return "redirect:/customer/invest/checkout#payment";
    }

    /** Step 3: pay. The OTP or UPI PIN is checked first, then the money moves in one transaction. */
    @PostMapping("/checkout/pay")
    public String pay(@RequestParam(required = false) PaymentMethod method, @RequestParam(required = false) String otp,
                      @RequestParam(required = false) String upiPin, HttpSession session, Principal principal,
                      RedirectAttributes redirect) {
        Order order = (Order) session.getAttribute(ORDER);
        if (order == null || !Boolean.TRUE.equals(session.getAttribute(KYC_OK))) {
            return "redirect:/customer/invest/checkout";
        }
        try {
            if (method == PaymentMethod.UPI) {
                String vpa = upi.vpaForCustomer(principal.getName())
                        .orElseThrow(() -> new InvalidRequestException("Activate JavaPay UPI first, or pay from your account"));
                upi.verifyPin(vpa, upiPin);
            } else if (method == PaymentMethod.ACCOUNT) {
                Instant expires = (Instant) session.getAttribute(OTP_EXPIRES);
                if (session.getAttribute(OTP) == null || expires == null || Instant.now().isAfter(expires)) {
                    throw new InvalidRequestException("Your OTP has expired. Tap \"Send OTP\" to get a new one");
                }
                if (!session.getAttribute(OTP).equals(otp == null ? "" : otp.trim())) {
                    throw new InvalidRequestException("Incorrect OTP. Please check the SMS and try again");
                }
            } else {
                throw new InvalidRequestException("Choose a payment method");
            }
            Investment inv = investments.pay(principal.getName(), order, method);
            session.removeAttribute(ORDER);
            session.removeAttribute(KYC_OK);
            session.removeAttribute(OTP);
            redirect.addFlashAttribute("paid", inv.getId());
            return "redirect:/customer/invest/done";
        } catch (BankException e) {
            redirect.addFlashAttribute("payError", e.getMessage());
            return "redirect:/customer/invest/checkout#payment";
        }
    }

    @PostMapping("/checkout/cancel")
    public String cancel(HttpSession session) {
        session.removeAttribute(ORDER);
        session.removeAttribute(KYC_OK);
        session.removeAttribute(OTP);
        return "redirect:/customer/invest";
    }

    @GetMapping("/done")
    public String done(Principal principal, Model model) {
        Object id = model.getAttribute("paid");
        if (id == null) {
            return "redirect:/customer/invest";
        }
        model.addAttribute("o", customers.overview(principal.getName()));
        model.addAttribute("h", investments.holding(principal.getName(), (Long) id));
        return "customer/invest-done";
    }

    private static BigDecimal parse(String amount) {
        try {
            return new BigDecimal(amount.trim().replace(",", ""));
        } catch (NullPointerException | NumberFormatException e) {
            throw new InvalidRequestException("Please enter a valid amount");
        }
    }
}
