# Bank Management System (Java + MySQL)

A desktop ATM / bank account application built with **Java Swing** and **MySQL** (JDBC).

## Features
- **Open a new account** with a 3-step application form: personal details, additional details (PAN, Aadhaar, income and so on), and account type and services
- A **card number and PIN** are generated for every new account
- **Sign in** with your card number and PIN
- **Deposit**, **withdraw** (checks your balance first) and **balance enquiry**
- Uses parameterised SQL queries (`PreparedStatement`), so it is protected against SQL injection

## Tech stack
Java 17 · Swing · JDBC · MySQL · JCalendar

## How to run

**1. Requirements:** Java 17 or newer, and MySQL running on `localhost:3306`.

**2. Create the database:**
```bash
mysql -u root -p < schema.sql
```

**3. Start the app:**
```bash
./run.sh
```

By default the app connects as `root` with no password. To use different settings, set them before running:
```bash
export DB_USER=root
export DB_PASSWORD=yourpassword
export DB_URL=jdbc:mysql://localhost:3306/bankmanagementsystem
./run.sh
```

## Project structure
| File | Purpose |
|---|---|
| `login.java` | Start screen: sign in or sign up |
| `SIGNUP1.java` | Application form page 1: personal details |
| `SignupTwo.java` | Application form page 2: additional details |
| `SignupThree.java` | Application form page 3: account type and services, generates the card number and PIN |
| `Transactions.java` | Deposit, withdraw and balance enquiry |
| `Conn.java` | MySQL connection |
| `schema.sql` | Database tables |
