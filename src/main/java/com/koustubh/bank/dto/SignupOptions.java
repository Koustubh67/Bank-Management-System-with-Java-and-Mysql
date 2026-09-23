package com.koustubh.bank.dto;

import java.util.List;

/** Choices shown in the account opening form's drop-downs. */
public final class SignupOptions {

    public static final List<String> GENDERS = List.of("Male", "Female", "Other");
    public static final List<String> MARITAL_STATUSES = List.of("Unmarried", "Married", "Other");
    public static final List<String> RELIGIONS = List.of("Hindu", "Muslim", "Sikh", "Christian", "Jain", "Buddhist", "Other");
    public static final List<String> CATEGORIES = List.of("General", "OBC", "SC", "ST", "Other");
    public static final List<String> INCOMES = List.of("None", "Below 1,50,000", "1,50,000 - 2,50,000",
            "2,50,000 - 5,00,000", "5,00,000 - 10,00,000", "Above 10,00,000");
    public static final List<String> EDUCATION = List.of("Below 10th", "10th Pass", "12th Pass", "Graduate",
            "Post Graduate", "Doctorate", "Other");
    public static final List<String> OCCUPATIONS = List.of("Salaried", "Self Employed", "Business", "Student",
            "Retired", "Government Employee", "Defence", "Other");
    public static final List<String> SERVICES = List.of("ATM Card", "Internet Banking", "Mobile Banking",
            "Email & SMS Alerts", "Cheque Book", "E-Statement");

    private SignupOptions() {
    }
}
