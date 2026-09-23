CREATE DATABASE IF NOT EXISTS bankmanagementsystem;
USE bankmanagementsystem;

CREATE TABLE IF NOT EXISTS signup(formno varchar(20), name varchar(20), father_name varchar(20), dob varchar(20), gender varchar(20), email varchar(30), marital_status varchar(20), address varchar(40), city varchar(25), pincode varchar(20), state varchar(25), country varchar(25));

CREATE TABLE IF NOT EXISTS signuptwo(formno varchar(20), religion varchar(20), category varchar(20), income varchar(20), education varchar(20), occupation varchar(30), pan varchar(20), aadhar varchar(20), seniorcitizen varchar(20), existingaccount varchar(20));

CREATE TABLE IF NOT EXISTS signupthree(formno varchar(20), accountType varchar(40), cardnumber varchar(25), pin varchar(10), facility varchar(100));

CREATE TABLE IF NOT EXISTS login(formno varchar(20), cardnumber varchar(25), pin varchar(10));

CREATE TABLE IF NOT EXISTS bank(cardnumber varchar(25), date varchar(50), type varchar(20), amount varchar(20));
