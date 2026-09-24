 # JavaBank: Bank Management System

A banking web application built with **Java 17, Spring Boot and MySQL**. Customers open an account online, withdraw
cash at a realistic web **ATM** (card insert, notes coming out of the cash slot, printed receipt), and pay each other
with **JavaPay UPI**, a phone-style UPI app with QR codes and a 6-digit UPI PIN. Bank staff approve and manage accounts
in an **admin panel**.

The project focuses on the correctness rules real banking software needs: exact money arithmetic, all-or-nothing
transfers, row locking under concurrent access, an append-only transaction ledger, and secure PIN handling.

![JavaBank home page](docs/screenshots/home.png)

## Features

**Account opening**
- 3-page application: personal details, KYC details (PAN, Aadhaar, income, occupation…), account type and services
- Input validation (PAN format, 12-digit Aadhaar, 6-digit PIN code, email) and duplicate-PAN/Aadhaar checks
- Generates a 12-digit account number and a 16-digit card number with a valid **Luhn check digit**, plus a 4-digit PIN
  shown **once**
- New accounts start as **PENDING** until bank staff approve them

**ATM**
- Log in with card number + PIN on the ATM keypad; the card slides into the reader and is ejected on exit
- Deposit, cash withdrawal, fast cash, balance enquiry, mini statement (last 10), fund transfer, PIN change
- Cash is paid out in the fewest ₹500 / ₹200 / ₹100 notes, which animate out of the cash slot until you take them
- A receipt prints out of the receipt slot; working side buttons, keypad beeps (can be muted), live clock

**JavaPay UPI**
- Activate UPI with the debit card + ATM PIN and set a 6-digit UPI PIN (the same step resets a forgotten PIN)
- UPI ID generated from the name and account number, e.g. `priya.3311@javabank`
- Pay a UPI ID: the payee's registered name is shown before you enter the UPI PIN on an NPCI-style PIN pad
- Receive money with a standard `upi://pay` QR code, generated server-side as SVG with ZXing
- Balance check (needs the UPI PIN, like real apps), history, animated payment-success screen
- ₹1,00,000 per transaction and per day; UPI locks after 3 wrong UPI PINs

**Security and banking rules**
- ATM PINs, UPI PINs and staff passwords stored as **BCrypt hashes**; the plain PIN is never saved
- Card **blocked after 3 wrong PINs**; the counter resets after a successful login
- **₹25,000 daily withdrawal limit**; cash amounts must be multiples of ₹100
- Frozen or pending accounts cannot log in or transact
- CSRF protection on every form; separate login sessions for ATM, UPI and staff

**Staff (admin) panel**
- Dashboard: customers, pending/active/frozen accounts, transactions, total deposits
- Approve, freeze and unfreeze accounts; unblock cards; unlock UPI
- Customer KYC view (Aadhaar masked) and full transaction history

## Screenshots

| ATM: collect your cash | ATM menu |
|---|---|
| ![Cash dispensing](docs/screenshots/atm-cash.png) | ![ATM menu](docs/screenshots/atm-menu.png) |

| UPI: enter UPI PIN | UPI: payment successful |
|---|---|
| ![UPI PIN pad](docs/screenshots/upi-pay.png) | ![UPI success](docs/screenshots/upi-success.png) |

| UPI home | Receive with QR |
|---|---|
| ![UPI home](docs/screenshots/upi-home.png) | ![UPI QR](docs/screenshots/upi-receive.png) |

| Staff dashboard | Account details |
|---|---|
| ![Dashboard](docs/screenshots/admin-dashboard.png) | ![Account](docs/screenshots/admin-account.png) |

## Architecture

```mermaid
flowchart LR
    Browser -->|HTTP| Web["Controllers<br/>(Spring MVC + Thymeleaf)"]
    Web --> Sec["Spring Security<br/>ATM: card + PIN · UPI: UPI ID + UPI PIN<br/>Staff: username + password"]
    Web --> Svc["Services<br/>AccountOpening · Atm · Transfer · Upi<br/>CardSecurity · Admin"]
    Svc --> Dom["Domain<br/>Account · Card · Customer · Transaction"]
    Svc --> Repo["Spring Data JPA repositories"]
    Repo --> DB[("MySQL<br/>schema managed by Flyway")]
```

```
src/main/java/com/koustubh/bank
├── config/      Security (three login chains), app settings, admin user seeding
├── domain/      JPA entities with the business rules (Account.debit/credit, Card lockout)
├── repository/  Spring Data repositories, incl. SELECT … FOR UPDATE queries
├── service/     Banking use cases, each running in one database transaction
├── dto/         Form objects with Bean Validation
├── web/         Controllers for signup, ATM and admin pages
└── exception/   Business errors with customer-friendly messages
```

## Design decisions

| Problem | How it is handled |
|---|---|
| Floating-point rounding errors with money | All amounts are `BigDecimal` in Java and `DECIMAL(15,2)` in MySQL |
| Two withdrawals at the same moment overdrawing an account | The account row is locked with `SELECT … FOR UPDATE` (pessimistic lock) before the balance is checked. A test fires 20 concurrent withdrawals at a ₹1,000 balance and asserts exactly 10 succeed |
| A transfer debiting one account but failing to credit the other | Both updates and both ledger entries run in one `@Transactional` method, so either all are saved or none |
| Deadlock when A→B and B→A transfer at the same time | Both accounts are always locked in ascending id order; covered by a concurrent test |
| Losing the wrong-PIN count when login fails | `@Transactional(noRollbackFor = …)` keeps the failed-attempt update for both ATM and UPI PINs |
| One transfer engine for ATM and UPI | `TransferService.moveMoney` does the locking, debit/credit and both ledger legs; UPI adds its own PIN check and limits on top |
| Paying out cash | `CashDispenser` picks the fewest notes (greedy works for 500/200/100); unit-tested for every amount |
| Audit trail | The `transactions` table is append-only; each row stores the balance after the operation, and both sides of a transfer share one reference id |
| Stolen database leaking PINs | PINs are BCrypt-hashed; Aadhaar is masked on screens |
| Schema changes | Versioned SQL migrations with Flyway; Hibernate only validates the schema |
| Testable "today" for daily limits | Time comes from an injected `Clock` in the bank's time zone (Asia/Kolkata) |

## Running locally

**Requirements:** Java 17+ and MySQL 8+ running on `localhost:3306`. Maven isn't needed; the included `./mvnw`
downloads it.

```bash
./mvnw spring-boot:run
```

Open <http://localhost:8080>. The `bankdb` database and its tables are created automatically on first start.

**Try it out**
1. Click **Open an account**, fill in the 3 pages and note the card number and PIN.
2. Go to **Staff** and log in with `admin` / `admin123`, then approve the account.
3. Go to **ATM**, log in with the card number and PIN, and deposit, withdraw or transfer money.
4. Go to **Login → JavaPay UPI → Activate**, enter the card number and ATM PIN and pick a 6-digit UPI PIN.
   Open a second account the same way and send it money by UPI ID.

**Configuration** (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/bankdb?createDatabaseIfNotExist=true` | Database connection |
| `DB_USER` / `DB_PASSWORD` | `root` / *(empty)* | Database login |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `admin123` | First staff login, created on startup. **Change this when deploying.** |
| `PORT` | `8080` | HTTP port |

## Tests

```bash
./mvnw test
```

86 tests run against an in-memory H2 database with the real Flyway schema:
- **Domain unit tests:** balance rules, account states
- **Service tests:** daily limit, insufficient funds, transfer atomicity, concurrent withdrawals, transfer deadlock
  avoidance, PIN lockout and unblock, PIN change
- **UPI tests:** activation, UPI ID format, payment and narration, daily and per-transaction limits, wrong-PIN lock and
  reset, staff unlock
- **Web tests (MockMvc):** full 3-page signup, ATM login with a wrong PIN, withdrawal, staff approval, access control,
  UPI activate → login → pay → QR → balance → history

GitHub Actions runs the build and all tests on every push.

## Docker

```bash
docker build -t javabank .
docker run -p 8080:8080 -e DB_URL=... -e DB_USER=... -e DB_PASSWORD=... -e ADMIN_PASSWORD=... javabank
```

## Tech stack

Java 17 · Spring Boot 4 · Spring MVC · Spring Security · Spring Data JPA / Hibernate · Flyway · MySQL · Thymeleaf ·
Bean Validation · ZXing (QR codes) · vanilla JS + CSS animations · JUnit 5 · AssertJ · MockMvc · H2 · Docker ·
GitHub Actions

## History

This project started as a college Java Swing desktop app. The original version is in the git history (commit
`9910afb`). It was rebuilt as a Spring Boot web application with a layered architecture, tests and security.

*JavaBank is a demo project and not a real bank.*
