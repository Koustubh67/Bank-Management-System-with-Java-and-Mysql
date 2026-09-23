package com.koustubh.bank.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;
    private String fatherName;
    private LocalDate dateOfBirth;
    private String gender;
    private String email;
    private String maritalStatus;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String country;

    private String religion;
    private String category;
    private String income;
    private String education;
    private String occupation;
    private String pan;
    private String aadhaar;
    private boolean seniorCitizen;
    private boolean existingAccount;

    private LocalDateTime createdAt;

    public Long getId() { return id; }
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
    public void setPan(String pan) { this.pan = pan; }
    public String getAadhaar() { return aadhaar; }
    public void setAadhaar(String aadhaar) { this.aadhaar = aadhaar; }
    public boolean isSeniorCitizen() { return seniorCitizen; }
    public void setSeniorCitizen(boolean seniorCitizen) { this.seniorCitizen = seniorCitizen; }
    public boolean isExistingAccount() { return existingAccount; }
    public void setExistingAccount(boolean existingAccount) { this.existingAccount = existingAccount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** Aadhaar is sensitive, so screens only ever show the last 4 digits. */
    public String getMaskedAadhaar() {
        return "XXXX-XXXX-" + aadhaar.substring(aadhaar.length() - 4);
    }
}
