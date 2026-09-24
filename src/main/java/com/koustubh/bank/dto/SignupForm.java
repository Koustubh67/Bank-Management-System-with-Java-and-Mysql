package com.koustubh.bank.dto;

import com.koustubh.bank.domain.AccountType;
import com.koustubh.bank.domain.Customer;
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
    @NotBlank(groups = Personal.class) @Size(max = 60, groups = Personal.class)
    private String fullName;
    @NotBlank(groups = Personal.class) @Size(max = 60, groups = Personal.class)
    private String fatherName;
    @NotNull(groups = Personal.class) @Past(groups = Personal.class)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;
    @NotBlank(groups = Personal.class)
    private String gender;
    @NotBlank(groups = Personal.class) @Email(groups = Personal.class) @Size(max = 100, groups = Personal.class)
    private String email;
    @NotBlank(groups = Personal.class)
    private String maritalStatus;
    @NotBlank(groups = Personal.class) @Size(max = 200, groups = Personal.class)
    private String address;
    @NotBlank(groups = Personal.class) @Size(max = 50, groups = Personal.class)
    private String city;
    @NotBlank(groups = Personal.class) @Size(max = 50, groups = Personal.class)
    private String state;
    @NotBlank(groups = Personal.class)
    @Pattern(regexp = "\\d{6}", message = "must be 6 digits", groups = Personal.class)
    private String pincode;
    @NotBlank(groups = Personal.class) @Size(max = 50, groups = Personal.class)
    private String country = "India";

    // Page 2: additional details
    @NotBlank(groups = Additional.class)
    private String religion;
    @NotBlank(groups = Additional.class)
    private String category;
    @NotBlank(groups = Additional.class)
    private String income;
    @NotBlank(groups = Additional.class)
    private String education;
    @NotBlank(groups = Additional.class)
    private String occupation;
    @NotBlank(groups = Additional.class)
    @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]", message = "must look like ABCDE1234F", groups = Additional.class)
    private String pan;
    @NotBlank(groups = Additional.class)
    @Pattern(regexp = "\\d{12}", message = "must be 12 digits", groups = Additional.class)
    private String aadhaar;
    @NotNull(groups = Additional.class)
    private Boolean seniorCitizen;
    @NotNull(groups = Additional.class)
    private Boolean existingAccount;

    // Page 3: account details
    @NotNull(groups = AccountDetails.class)
    private AccountType accountType;
    private List<String> services = new ArrayList<>();
    @AssertTrue(message = "you must accept the declaration", groups = AccountDetails.class)
    private boolean declaration;

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
    public UploadedFile getPanDocument() { return panDocument; }
    public void setPanDocument(UploadedFile panDocument) { this.panDocument = panDocument; }
    public UploadedFile getAadhaarDocument() { return aadhaarDocument; }
    public void setAadhaarDocument(UploadedFile aadhaarDocument) { this.aadhaarDocument = aadhaarDocument; }
    public void setDeclaration(boolean declaration) { this.declaration = declaration; }
}
