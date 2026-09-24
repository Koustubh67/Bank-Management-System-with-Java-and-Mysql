package com.koustubh.bank.dto;

import com.koustubh.bank.domain.AccountType;
import com.koustubh.bank.domain.Customer;
import com.koustubh.bank.service.CustomerLoginService;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The 3-page account opening form. It is kept in the HTTP session between pages;
 * each page validates only its own group of fields.
 */
public class SignupForm implements Serializable {

    public interface Personal {}
    public interface Additional {}
    public interface AccountDetails {}

    /** Highest page completed so far, so pages cannot be skipped. */
    private int completedStep;

    // Page 1: personal details
    @NotBlank(message = "Enter your full name", groups = Personal.class) @Size(max = 60, groups = Personal.class)
    private String fullName;
    @NotBlank(message = "Enter your father's name", groups = Personal.class) @Size(max = 60, groups = Personal.class)
    private String fatherName;
    @NotNull(message = "Enter your date of birth", groups = Personal.class)
    @Past(message = "Date of birth must be in the past", groups = Personal.class)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;
    @NotBlank(message = "Choose your gender", groups = Personal.class)
    private String gender;
    @NotBlank(message = "Enter your email address", groups = Personal.class)
    @Email(message = "Enter a valid email address", groups = Personal.class)
    @Size(max = 100, groups = Personal.class)
    private String email;
    @NotBlank(message = "Enter your mobile number", groups = Personal.class)
    @Pattern(regexp = "[6-9]\\d{9}", message = "Enter a valid 10-digit Indian mobile number", groups = Personal.class)
    private String mobile;
    @NotBlank(message = "Choose your marital status", groups = Personal.class)
    private String maritalStatus;
    @NotBlank(message = "Enter your address", groups = Personal.class) @Size(max = 200, groups = Personal.class)
    private String address;
    @NotBlank(message = "Enter your city", groups = Personal.class) @Size(max = 50, groups = Personal.class)
    private String city;
    @NotBlank(message = "Enter your state", groups = Personal.class) @Size(max = 50, groups = Personal.class)
    private String state;
    @NotBlank(message = "Enter your PIN code", groups = Personal.class)
    @Pattern(regexp = "\\d{6}", message = "PIN code must be 6 digits", groups = Personal.class)
    private String pincode;
    @NotBlank(message = "Enter your country", groups = Personal.class) @Size(max = 50, groups = Personal.class)
    private String country = "India";

    // Page 2: additional details
    @NotBlank(message = "Choose your religion", groups = Additional.class)
    private String religion;
    @NotBlank(message = "Choose your category", groups = Additional.class)
    private String category;
    @NotBlank(message = "Choose your annual income", groups = Additional.class)
    private String income;
    @NotBlank(message = "Choose your qualification", groups = Additional.class)
    private String education;
    @NotBlank(message = "Choose your occupation", groups = Additional.class)
    private String occupation;
    @NotBlank(message = "Enter your PAN", groups = Additional.class)
    @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]", message = "PAN must look like ABCDE1234F", groups = Additional.class)
    private String pan;
    @NotBlank(message = "Enter your Aadhaar number", groups = Additional.class)
    @Pattern(regexp = "\\d{12}", message = "Aadhaar must be 12 digits", groups = Additional.class)
    private String aadhaar;
    @NotNull(message = "Tell us if you are a senior citizen", groups = Additional.class)
    private Boolean seniorCitizen;
    @NotNull(message = "Tell us if you already bank with us", groups = Additional.class)
    private Boolean existingAccount;

    // Page 3: account details
    @NotNull(message = "Choose an account type", groups = AccountDetails.class)
    private AccountType accountType;
    private List<String> services = new ArrayList<>();
    @AssertTrue(message = "Please accept the declaration", groups = AccountDetails.class)
    private boolean declaration;

    // Net banking password, chosen on page 3. Only its hash is saved.
    @NotBlank(message = "Create a password", groups = AccountDetails.class)
    @Pattern(regexp = CustomerLoginService.PASSWORD_RULE, message = "Password " + CustomerLoginService.PASSWORD_HINT,
            groups = AccountDetails.class)
    private String password;
    private String confirmPassword;

    @AssertTrue(message = "Passwords do not match", groups = AccountDetails.class)
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }

    // KYC documents, uploaded on page 2 and checked by KycFiles
    private UploadedFile panDocument;
    private UploadedFile aadhaarDocument;

    public Customer toCustomer() {
        Customer c = new Customer();
        c.setFullName(fullName.trim());
        c.setFatherName(fatherName.trim());
        c.setDateOfBirth(dateOfBirth);
        c.setGender(gender);
        c.setEmail(email.trim());
        c.setMobile(mobile);
        c.setMaritalStatus(maritalStatus);
        c.setAddress(address.trim());
        c.setCity(city.trim());
        c.setState(state.trim());
        c.setPincode(pincode);
        c.setCountry(country.trim());
        c.setReligion(religion);
        c.setCategory(category);
        c.setIncome(income);
        c.setEducation(education);
        c.setOccupation(occupation);
        c.setPan(pan);
        c.setAadhaar(aadhaar);
        c.setSeniorCitizen(seniorCitizen);
        c.setExistingAccount(existingAccount);
        return c;
    }

    public int getCompletedStep() { return completedStep; }
    public void setCompletedStep(int completedStep) { this.completedStep = completedStep; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getFatherName() { return fatherName; }
    public void setFatherName(String fatherName) { this.fatherName = fatherName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile == null ? null : mobile.replaceAll("[\\s-]", "").replaceFirst("^(\\+91|0)", ""); }
    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getReligion() { return religion; }
    public void setReligion(String religion) { this.religion = religion; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getIncome() { return income; }
    public void setIncome(String income) { this.income = income; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }
    public String getPan() { return pan; }
    public void setPan(String pan) { this.pan = pan == null ? null : pan.trim().toUpperCase(); }
    public String getAadhaar() { return aadhaar; }
    public void setAadhaar(String aadhaar) { this.aadhaar = aadhaar == null ? null : aadhaar.replaceAll("\\s", ""); }
    public Boolean getSeniorCitizen() { return seniorCitizen; }
    public void setSeniorCitizen(Boolean seniorCitizen) { this.seniorCitizen = seniorCitizen; }
    public Boolean getExistingAccount() { return existingAccount; }
    public void setExistingAccount(Boolean existingAccount) { this.existingAccount = existingAccount; }
    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }
    public List<String> getServices() { return services; }
    public void setServices(List<String> services) { this.services = services == null ? new ArrayList<>() : services; }
    public boolean isDeclaration() { return declaration; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    public UploadedFile getPanDocument() { return panDocument; }
    public void setPanDocument(UploadedFile panDocument) { this.panDocument = panDocument; }
    public UploadedFile getAadhaarDocument() { return aadhaarDocument; }
    public void setAadhaarDocument(UploadedFile aadhaarDocument) { this.aadhaarDocument = aadhaarDocument; }
    public void setDeclaration(boolean declaration) { this.declaration = declaration; }
}
