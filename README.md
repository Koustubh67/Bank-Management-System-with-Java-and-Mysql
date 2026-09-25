 # JavaBank: Bank Management System

A banking web application built with **Java 17, Spring Boot and MySQL**. Customers open an account online, withdraw
cash at a realistic web **ATM** (card insert, notes coming out of the cash slot, printed receipt), and pay each other
with **JavaPay UPI**, a phone-style UPI app with QR codes and a 6-digit UPI PIN. They invest in real mutual funds, take
**loans at a fixed or floating rate** with a full EMI schedule, plan with free **money tools** (EMI, eligibility, SIP,
lump sum, FD), and request insurance. Bank staff approve accounts and loans in an **admin panel**.

The project focuses on the correctness rules real banking software needs: exact money arithmetic, all-or-nothing
transfers, row locking under concurrent access, an append-only transaction ledger, and secure PIN handling.

![JavaBank home page](docs/screenshots/home.png)

## Features

**Net banking login (security first)**
- The public site is branding only. The dashboard, passbook, profile, ATM and UPI all need a customer login
- Customers log in with a **Customer ID + password** chosen at account opening (BCrypt-hashed). 5 wrong passwords
  lock the login; customers reset it with their debit card + ATM PIN, or staff unlock it
- Customer and staff logins are separate Spring Security filter chains with separate sessions
- **Back button / swipe-back safe:** signed-in pages are sent with `Cache-Control: no-store`, reload themselves if the
  browser restores them from its back-forward cache, logout sends `Clear-Site-Data` (on HTTPS), and visiting `/login`
  while signed in goes straight back to the dashboard
- **Dashboard:** balance hidden until you tap "Show", account and card status, quick actions, recent transactions
- **Passbook:** date and credit/debit filters, pagination, and a **CSV statement download** (protected against
  spreadsheet formula injection)
- **Profile:** personal and KYC details (PAN and Aadhaar masked), UPI ID, change password

**Public Invest marketplace (no login needed, like Groww / Zerodha Coin)**
- `/invest`, linked from the home page: a "Market today" Nifty 50 chart (tracked through the UTI Nifty 50 Index
  Fund's NAV), top gainers and losers on the last NAV, best 5-year returns, collections (high growth, tax saver,
  index, low risk, gold), and all **17 real funds** with search, category chips, sorting and 1-year sparklines
- FD rates table with a maturity calculator, and a SIP calculator
- `/invest/funds/{code}`: every fund's live interactive NAV chart, 1Y/3Y/5Y returns, facts, and a **"What if you had
  invested?"** calculator that replays a SIP or one-time investment on the fund's **real historical NAVs**
- **Invest now** asks for login (or a free account) and then returns straight to that fund. Prices are fetched in
  parallel and preloaded at startup, so the page opens in about 0.2 s

**Investments with real market data**
- **17 real mutual funds** (e.g. Parag Parikh Flexi Cap, HDFC Mid Cap, Quant Small Cap, UTI Nifty 50 Index, Mirae
  Asset ELSS, HDFC Balanced Advantage, SBI Gilt, Axis Gold; all Direct · Growth). Daily NAVs come live from
  [mfapi.in](https://www.mfapi.in), a free public API that republishes AMFI data, with a 6-hour cache and a clear
  "prices unavailable" state instead of made-up numbers
- Fund pages show the real **1-year NAV chart and 1Y / 3Y / 5Y returns** (CAGR) calculated from NAV history
- **SIP or one-time purchase**: units are bought at the real NAV, so the portfolio's **current value and profit or
  loss move with the market**. A scheduled job debits due SIP instalments every month (staff can run it on demand)
- **Checkout like a real app:** choose → confirm KYC (PAN, Aadhaar, mobile must match; every mismatch shown at once)
  → pay from the account with an **OTP** sent by (simulated) SMS, or with **JavaPay UPI** and the UPI PIN
- **Fixed deposits** for 1–5 years (6.80%–7.25% p.a., compounded quarterly) with interest accrued to date
- Every holding has its own page (units, NAV, average cost, returns, next SIP date)
- **Interactive charts everywhere** (no chart library, plain SVG + JS): portfolio value on the dashboard and Invest
  page, each holding's value, and each fund's NAV. Ranges 1M / 6M / 1Y / 3Y / 5Y / All, green when up and red when
  down, an "invested" dashed line that steps up with each SIP instalment, and a hover/touch tooltip with the exact
  date, value and profit. Values come from real NAV history × the units held on each day
- **Live updates:** pages poll `/customer/invest/api/live` every minute and flash ▲/▼ when a value changes; every
  fund and holding shows its real move on the latest NAV ("▲ 0.16% today"). Mutual fund NAVs are published once per
  business day by AMFI, so that is how often the real numbers change

**Insurance through an expert callback (like real bancassurance)**
- Health, term life, motor and travel plans with what's covered. **"Talk to an expert"** takes the customer's details
  (cover wanted, family/nominee/vehicle/trip, best time to call); the customer is told an expert will call within
  24 hours, and gets an SMS and email
- Staff work an **insurance queue**: mark as contacted, **issue the policy** (insurer, policy number, cover, premium;
  the first premium is auto-debited) or close it with a reason. Customers track each request and open every
  **issued policy** on its own page
- No premiums are made up: prices come from the insurer through the expert, as in a real bank

**Loans with fixed or floating rates, approval and auto-debited EMIs**
- Public `/loans` page (no login): home, car, personal, education, two-wheeler and gold loans with an **EMI
  calculator** for each (principal vs interest split, year-by-year repayment table)
- **Fixed or floating, the customer chooses.** Floating rates are linked to the **real RBI repo rate (5.25%)**, like
  the repo-linked (EBLR) loans Indian banks offer: rate = repo rate + a spread per loan type (home 8.50%). Fixed
  rates cost a small premium (home 9.50%) and never change
- **Repo rate changes reprice floating loans:** a branch manager records the new repo rate on the staff Loans page;
  in one transaction every active floating loan gets its new rate, the EMIs not yet paid and due after today are
  recalculated on the principal still owed, the **end date stays the same**, and the customer is alerted with the old
  and new EMI. Paid and overdue EMIs never change. Customer and staff both see a **rate change history**. Officers
  can see the repo rate but only a branch manager can change it
- **Not a customer yet?** A "request a call back" form takes name, mobile, email, city, loan wanted, income and best
  time to call (every error shown at once, nothing retyped), then says **"our loan expert will call you within 24
  hours"** with an enquiry reference. Staff see these enquiries and mark them contacted or closed
- **Existing customers apply online** from their dashboard: live EMI and affordability check while typing. The bank's
  **FOIR** rule (EMI ≤ 50% of monthly income) is enforced on the server too; one open application per loan type
- **Staff approve or reject:** the officer sees the customer, balance, income, FOIR and EMI at the list rate, then
  sanctions the amount (≤ requested), rate and tenure with a live EMI preview. On approval the money is **disbursed to
  the savings account** in the same database transaction and the full **EMI schedule** is created. Rejection needs a
  reason, shown to the customer. Two officers clicking approve at once can't disburse twice (row lock + status check)
- **Both sides see the dates:** EMI amount, **EMI date** (same day every month), **first EMI** (one month after
  disbursal) and **loan end date**, plus progress, interest paid and each instalment's principal/interest split
- **EMIs are auto-debited** every morning at 09:40 (staff can run it on demand). If the balance is short the EMI turns
  **overdue**, the customer gets one alert, and it is retried daily. Paying the last EMI **closes the loan**
- **Pay an EMI yourself, three ways:**
  - **UPI:** a real `upi://pay` QR code (payee, amount, reference) with a **5-minute countdown**; the page checks every
    3 seconds whether the money has arrived. The demo has no UPI network, so an "I've paid" button stands in for the
    UPI app's confirmation
  - **Debit / credit card:** card number checked with the **Luhn algorithm**, network detected (Visa, Mastercard,
    RuPay, Amex), expiry and CVV validated, every problem shown at once; then an **OTP by SMS** (BCrypt-hashed,
    3 tries) that must be entered **within 5 minutes**. Only the network, last 4 digits and name are stored, never
    the full number or the CVV. Test cards: `4111 1111 1111 1111` or `5555 5555 5555 4444`
  - **Savings account:** paid at once through the ledger
- **Success screen, receipt and alert:** receipt number, UTR / authorisation code, principal and interest, principal
  still owed and the next EMI; a printable **EMI payment receipt** (bill with the amount in words, "Save as PDF"); an
  **email and SMS** with the receipt. Every paid EMI shows **PAID with its receipt** in the schedule, and a payments
  history lists every attempt (paid, expired, failed, cancelled). Staff can open any receipt
- **Never paid twice:** paying locks the loan, then checks the EMI is still unpaid and still the same amount (a repo
  rate reset can change it); a double click, a second tab or the auto-debit can't take the money again, and a unique
  database constraint allows only one successful payment per EMI. Expired or cancelled payments take nothing
- Reducing-balance EMI maths in `BigDecimal`: interest is rounded to the paisa each month and the last EMI absorbs
  the rounding, so the schedule ends at exactly ₹0

**Money tools (no login)**
- `/tools`, a **Tools** tab in the top menu and a **05 Tools** tab on the home page: EMI calculator, loan
  eligibility ("how much can I borrow?" with the same 50%-of-income rule the bank applies), SIP, lump sum and FD
- **Type any value or drag the slider:** every slider has an editable box that accepts exact amounts and Indian
  shorthand (`25,00,000`, `25L`, `1.2Cr`); out-of-range or unreadable input shows a hint and is corrected when you
  leave the box. Tenure can be entered in years or months
- **Fixed or floating?** The EMI calculator compares both side by side with a "what if the repo rate changes by x%
  from year n" scenario (recalculated exactly the way the bank resets a floating loan), says which option is cheaper,
  and works out the **break-even rate rise**
- Checked against known results: ₹10,000/month SIP at 12% for 15 years = ₹50,45,760 (same as Groww); ₹25 lakh at
  8.5% for 20 years = ₹21,695.58 EMI

**SMS & email alerts (simulated)**
- Application received / approved / declined, investment confirmed, OTPs, SIP instalment missed, insurance request
  received, policy issued, loan approved / rejected, EMI paid / overdue, loan closed. Stored in an outbox shown on the customer dashboard and to staff, and written to the log
  (plug in any SMS/email provider in `NotificationService`)

**Account opening and KYC**
- 3-page application: personal details, KYC details (PAN, Aadhaar, income, occupation…), account type and services
- **All errors shown at once** in a summary at the top of each page, including **already-registered** mobile
  numbers (page 1) and PAN / Aadhaar (page 2), so nothing is discovered only at the end. Passwords and uploaded files
  are kept when another field has an error
- A **mobile number** is collected for SMS alerts; after submitting, customers are told they'll be notified by SMS and
  email once approved
- **Upload PAN and Aadhaar card images** (PDF, JPG or PNG, up to 2 MB, drag-and-drop with preview). The file type is
  checked from the file's first bytes, so a renamed HTML or script file is refused
- **Track application** page: enter the account number **or Customer ID** + PAN to see a live timeline (submitted → documents received →
  verification → active or declined, with the reason)
- Input validation (PAN format, 12-digit Aadhaar, 6-digit PIN code, email) and duplicate-PAN/Aadhaar checks
- Generates a 12-digit account number and a 16-digit card number with a valid **Luhn check digit**, plus a 4-digit PIN
  shown **once**
- New accounts start as **PENDING** until bank staff approve them

**ATM**
- Opened from the customer dashboard; the customer's own card slides into the reader, then the ATM PIN is entered on
  the keypad. On exit the card is ejected and you return to the dashboard
- Deposit, cash withdrawal, fast cash, balance enquiry, mini statement (last 10), fund transfer, PIN change
- Cash is paid out in the fewest ₹500 / ₹200 / ₹100 notes, which animate out of the cash slot until you take them
- A receipt prints out of the receipt slot; working side buttons, keypad beeps (can be muted), live clock

**JavaPay UPI**
- Opened from the customer dashboard. Activate UPI with the card's ATM PIN and set a 6-digit UPI PIN (the same step
  resets a forgotten UPI PIN)
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

**Home page (award-style, built to sell)**
- About 5 screens long: a full-screen dark hero with a rotating headline (save → pay → grow → protect), live
  "Trusted by N+ customers" numbers, a scrolling highlights marquee, **one product showcase with auto-advancing tabs**
  (Accounts · UPI · Invest with a SIP/FD calculator · Insure), a security bento grid, a 4-step call to action and a
  short FAQ
- Interaction details: word-by-word headline reveal, magnetic buttons, custom cursor, grain texture, scroll progress
  bar. All motion switches off for users who prefer reduced motion

**Staff (admin) panel and staff accounts**
- Two roles: **branch manager** (ADMIN) and **bank officer**. Managers add staff; each new staff member gets a
  one-time temporary password, logs in on the Bank staff tab, and **must set their own password** before anything
  else. Managers can reset passwords and disable logins (never their own); officers can't manage staff
- Approvals and declines record **which staff member** did them, and the customer is alerted
- Pages: dashboard, accounts (KYC documents, holdings with live P&L, policies, insurance requests, loans, alerts
  sent), loans (repo rate, applications, active loans, website enquiries), insurance queue, all investments, transactions, alerts
  sent, staff
- Dashboard: customers, pending/active/frozen accounts, loan applications, overdue EMIs, new loan enquiries,
  transactions, total deposits
- Review the uploaded PAN and Aadhaar documents next to the customer's details
- **Approve or decline** applications (decline needs a reason, shown to the customer); freeze and unfreeze accounts;
  unblock cards; unlock UPI
- Customer KYC view (Aadhaar masked) and full transaction history

## Screenshots

| Tools: EMI with fixed vs floating (no login) | Floating loan after a repo rate change |
|---|---|
| ![EMI calculator](docs/screenshots/tools-emi.png) | ![Rate change](docs/screenshots/loan-rate-change.png) |

| Pay an EMI by UPI: QR code with a 5-minute timer | EMI payment receipt |
|---|---|
| ![UPI payment](docs/screenshots/emi-pay-upi.png) | ![Receipt](docs/screenshots/emi-receipt.png) |

| Loans (no login) | Customer's loan: EMI dates and schedule |
|---|---|
| ![Loans page](docs/screenshots/loans.png) | ![Customer loan](docs/screenshots/customer-loan.png) |

| Staff: loan applications and enquiries | Staff: approve with live EMI |
|---|---|
| ![Staff loans](docs/screenshots/admin-loans.png) | ![Approve loan](docs/screenshots/admin-loan-approve.png) |

| Public fund page (no login) | Holding with live value chart |
|---|---|
| ![Public fund page](docs/screenshots/market-fund.png) | ![Holding chart](docs/screenshots/holding-chart.png) |

| Fund NAV chart (real data) |
|---|
| ![Fund chart](docs/screenshots/fund-chart.png) |

| Customer dashboard | Invest |
|---|---|
| ![Dashboard](docs/screenshots/dashboard.png) | ![Invest](docs/screenshots/invest.png) |

| KYC document upload | Track application |
|---|---|
| ![KYC upload](docs/screenshots/kyc-upload.png) | ![Track application](docs/screenshots/track-application.png) |

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
    Web --> Sec["Spring Security<br/>Customer: Customer ID + password (then ATM PIN / UPI PIN)<br/>Staff: username + password"]
    Web --> Svc["Services<br/>AccountOpening · Atm · Transfer · Upi<br/>CardSecurity · Admin"]
    Svc --> Dom["Domain<br/>Account · Card · Customer · Transaction"]
    Svc --> Repo["Spring Data JPA repositories"]
    Repo --> DB[("MySQL<br/>schema managed by Flyway")]
```

```
src/main/java/com/koustubh/bank
├── config/      Security (customer + staff login chains), app settings, admin user seeding
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
| Unsafe file uploads | KYC files are accepted only if their first bytes are a real PDF, PNG or JPEG signature, file names are cleaned, and staff downloads are sent with `X-Content-Type-Options: nosniff` |
| EMI schedule not adding up to the loan | Reducing-balance formula in `BigDecimal`; interest rounded to the paisa each month and the last EMI absorbs the rounding, so the balance ends at exactly ₹0 (tested for tenures up to 30 years) |
| A loan approved twice, or disbursed without a schedule | The loan row is locked (`FOR UPDATE`) and must still be `APPLIED`; disbursal credit, ledger entry and all instalments are saved in one transaction. A test has two officers approve at the same moment |
| An EMI paid twice (double click, two tabs, auto-debit during a UPI payment) | Every payment locks the loan and re-reads the payment, then checks the EMI is still unpaid at the same amount; a unique constraint on the paid instalment backs it up in the database. Tests confirm the same QR code from two threads at once and pay an EMI while the auto-debit runs |
| Storing card data | Only network, last 4 digits and name are kept; the CVV is never stored or sent back to the page; the OTP is BCrypt-hashed and expires with the payment after 5 minutes |
| A loan approved at the same moment as a repo rate change, priced off the old rate | Approvals and repo rate changes both lock the single repo rate row first, then loans in id order, then accounts, so they run one after the other and can't deadlock with EMI collection. A test runs both at the same moment and checks rate = current repo + spread |
| Repricing a floating loan without breaking its history | Only EMIs that are unpaid and due after today are recalculated, on the balance after the last earlier EMI; paid and overdue EMIs keep their amounts, the end date stays, and each reset is stored in `loan_rate_change` |
| One failing loan stopping the nightly EMI run | Each loan is collected in its own transaction (`TransactionTemplate`), oldest EMI first; a short balance marks it overdue with a single alert and is retried next day |
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

## Demo accounts (test data)

On first start the app loads 6 demo customers with **fixed** card numbers and PINs, so you can try every feature
without signing up. Each one is in a different state. They come from
[`DemoDataSeeder`](src/main/java/com/koustubh/bank/demo/DemoDataSeeder.java) and are loaded only once; set
`DEMO_DATA=false` to turn this off.

> These are fake test numbers for this demo only. Never use real card numbers, PINs or Aadhaar numbers.

**Log in at <http://localhost:8080/login>.** There are exactly two logins:

- **Customer:** Customer ID + password. **Every demo customer's password is `Demo@1234`.** After logging in you get
  the dashboard (balance, recent transactions), passbook (filters + CSV download) and profile. The **ATM** and
  **JavaPay UPI** open from the dashboard; the ATM then asks for the card's ATM PIN, and UPI asks for the UPI PIN.
- **Bank staff:** branch manager `admin` / `admin123`, or bank officer `neha.officer` / `Officer@123` (Bank staff tab).

| Customer | Customer ID | Account no. | Card number | ATM PIN | UPI ID | UPI PIN | Balance | State: what to try |
|---|---|---|---|---|---|---|---|---|
| Rahul Sharma | `JB10000001` | `100000000001` | `5040930000000017` | `1234` | `rahul.0001@javabank` | `123456` | ₹42,350 | ✅ Active: everything. 12-month Parag Parikh SIP (real P&L), health policy, open term-life request, **₹6 lakh fixed-rate car loan** (5 of 60 EMIs paid) |
| Priya Verma | `JB10000002` | `100000000002` | `5040930000000025` | `2345` | `priya.0002@javabank` | `234567` | ₹1,20,950 | ✅ Active current account. 24-month UTI Nifty 50 SIP, motor policy, **₹45 lakh floating-rate home loan application** waiting for staff |
| Amit Patel | `JB10000003` | `100000000003` | `5040930000000033` | `3456` | — | — | ₹0 | ⏳ **Pending**: dashboard says "under review"; approve or decline him as staff |
| Sneha Iyer | `JB10000004` | `100000000004` | `5040930000000041` | `4567` | — | — | ₹20,000 | ❄️ **Frozen**: ATM refuses; unfreeze as staff |
| Vikram Singh | `JB10000005` | `100000000005` | `5040930000000058` | `5678` | — | — | ₹15,000 | 🚫 **Card blocked** (3 wrong PINs): unblock as staff |
| Anjali Gupta | `JB10000006` | `100000000006` | `5040930000000066` | `6789` | `anjali.0006@javabank` | `345678` | ₹7,700 | 🔒 **UPI locked**: unlock as staff, or set a new UPI PIN from UPI setup |

Demo mobile numbers are `98765000` + the last two digits of the account number (Rahul: `9876500001`), used for
KYC confirmation at investment checkout. Balances are for a fresh database; on a database created by an older
version of the app they can differ.

There is also a public loan enquiry from **Arjun Mehta** (not a customer) waiting for a call back on the staff
**Loans** page.

The demo data also includes real transaction history (deposits, withdrawals, an ATM transfer and UPI payments with
notes like "Dinner" and "Movie tickets"), so mini statements and UPI history aren't empty.

**Quick tour (5 minutes)**
1. **Login:** go to **Login**, choose **Customer**, enter `JB10000001` / `Demo@1234`. Tap **Show** to reveal the balance,
   then open **Passbook**, filter by date and **Download CSV**.
2. **ATM:** from the dashboard open **ATM** → enter PIN `1234` → **Cash Withdrawal** → `1800`, and watch
   3 × ₹500 + ₹200 + ₹100 come out of the cash slot. **Exit** returns you to the dashboard.
3. **UPI:** from the dashboard open **JavaPay UPI** → **Pay** → `priya.0002@javabank`, ₹500 → UPI PIN `123456`. Open
   **Receive** to see Rahul's QR code.
4. **Security:** log in as Amit (`JB10000003`): the dashboard shows the account is under review and the ATM is locked.
   Enter 5 wrong passwords for any customer to lock the login, then reset it with **Forgot or set password**
   (card number + ATM PIN).
5. **Invest:** as Rahul open **Invest**: see his SIP's real profit or loss, open a fund (real NAV chart and returns),
   start a SIP → confirm KYC (PAN `ABCPS1234A`, Aadhaar `999900000001`, mobile `9876500001`) → **Send OTP** → pay.
6. **Insure:** open **Insurance** → **Talk to an expert** → request a callback. Then log in as staff, open
   **Insurance**, and issue the policy; back as Rahul, open the new policy from the dashboard.
7. **Loans:** open **Tools** from the home page, try the EMI calculator (type `25L` as the amount), switch between
   fixed and floating and set a repo rate change. As Rahul open **Loans** to see his car loan's EMI date, end date and
   schedule, then **apply** for a personal loan (₹2,00,000, 24 months, income ₹85,000) and pick **Floating**. As
   staff open **Loans** → **Review & decide** → approve: the money lands in Rahul's account and both sides show the
   first EMI and the loan end date. Back as Rahul, **Pay this EMI now**: try **UPI** (QR code and 5-minute timer,
   then "I've paid"), or **card** `4111 1111 1111 1111` with any future expiry and CVV (the OTP appears as an SMS on
   screen). Open the receipt and **Save as PDF**; the email alert is on the dashboard.
8. **Repo rate change:** as `admin`, on **Loans** enter a new repo rate (e.g. `5.50`) and a note → **Change & reprice
   loans**. Rahul's floating loan moves up by 0.25%, his EMIs from the next due date change, and his loan page shows
   the rate change. Set it back to `5.25` afterwards.
9. **Staff:** log in as `admin` / `admin123`, approve Amit, unblock Vikram's card, unlock Anjali's UPI, unfreeze Sneha.
10. **New staff:** as `admin` open **Staff**, add an officer, then log in as them with the temporary password: you'll be
   asked to set a new one. Officers can't open the Staff page.
11. **Sign up with KYC:** open your own account with **Open account** and upload any sample image as the PAN and
   Aadhaar card (never real documents). As staff, open the application, view the documents and **decline** it with a
   reason. Then open **Track application** (account number + PAN) to see the reason.
12. **Home page:** try the SIP / FD calculator in the Invest tab, or open the **Tools** tab.

Tests check that every login in this table works (`DemoDataSeederTest`), so the table stays correct.

**Configuration** (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/bankdb?createDatabaseIfNotExist=true` | Database connection |
| `DB_USER` / `DB_PASSWORD` | `root` / *(empty)* | Database login |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `admin123` | First staff login, created on startup. **Change this when deploying.** |
| `DEMO_DATA` | `true` | Load the demo customers above on first start |
| `PORT` | `8080` | HTTP port |

## Tests

```bash
./mvnw test
```

165 tests run against an in-memory H2 database with the real Flyway schema:
- **Domain unit tests:** balance rules, account states
- **Service tests:** daily limit, insufficient funds, transfer atomicity, concurrent withdrawals, transfer deadlock
  avoidance, PIN lockout and unblock, PIN change
- **UPI tests:** activation, UPI ID format, payment and narration, daily and per-transaction limits, wrong-PIN lock and
  reset, staff unlock
- **KYC tests:** file type detection from content, oversized and disguised files, file name cleaning, upload →
  staff view → decline → tracking page
- **Investment tests** (with a fixed fake NAV source, never the internet): units at NAV, market profit on a year-old
  SIP, monthly SIP debits and missed instalments, FD maths and accrual, order rules; web checkout with KYC mismatches,
  wrong/right OTP, dashboard and passbook
- **Marketplace tests:** public browsing without login, public chart JSON, "what if" maths on NAV history, and
  "Invest now" → login → back to the same fund
- **Chart tests:** NAV series ordering and ranges, SIP value series stepping up and ending at the holding's value,
  portfolio series across funds and FDs, charts only for the owner, JSON endpoints and live feed
- **Insurance tests:** all form errors at once, callback → contacted → policy issued with premium debit, close with
  reason, policy privacy between customers
- **Loan tests:** EMI formula against the textbook value (₹1 lakh, 10%, 12 months → ₹8,791.59), schedules that end at
  exactly ₹0 for many amounts/rates/tenures, first-EMI date on month ends; every application error at once, FOIR
  limit, one open application per type, approve once only, **two officers approving at the same time** → one
  disbursal, overdue EMI → one alert → collected after a deposit, paying every EMI closes the loan with the right
  balance, customers can't see or pay other customers' loans; web flow: public enquiry, apply → staff approve → both
  sides show the first EMI and end date, rejection reason shown to the customer
- **Rate tests:** floating = repo + spread and fixed = floating + premium for every product; the customer's choice sets
  the quote; floating can't be sanctioned below the repo rate; a repo rise reprices only future unpaid EMIs (paid and
  overdue ones untouched, same dates, principal repaid exactly, ends at ₹0, customer alerted) and leaves fixed loans
  alone; all repo-rate errors at once; **an approval and a repo change at the same moment** still give
  rate = current repo + spread; only a branch manager can change the repo rate; the Tools page is public
- **EMI payment tests:** every card error at once, Luhn and card networks, card number and CVV never stored, OTP sent
  by SMS and BCrypt-hashed, 3 wrong OTPs stop the payment, the right OTP pays without touching the savings account,
  a UPI QR code refused after 5 minutes, unfinished payments expire on their own, a new payment cancels the pending one,
  the auto-debit paying an EMI while its QR code is open, **two tabs confirming the same QR code at once pay once**,
  receipts private to their owner; web flow: choose method → card errors kept (except CVV) → OTP page → success →
  receipt, and UPI QR → status polling → success; amount in words in Indian numbering
- **Staff tests:** temporary password → forced change, duplicate usernames, officers blocked from staff management,
  disabled logins, admins can't lock themselves out
- **Security tests:** no-store cache headers, Clear-Site-Data on logout, `/login` redirect while signed in
- **Demo data tests:** every demo login, balance and account state in the table above
- **Login tests:** Customer ID login, 5-attempt lock, reset with debit card, staff unlock, change password, every
  banking page redirects to login, customer and staff sessions can't cross over
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
[`9910afb`](https://github.com/Koustubh67/Bank-Management-System-with-Java-and-Mysql/tree/9910afb)). It was rebuilt as a Spring Boot web application with a layered architecture, tests and security.

*JavaBank is a demo project and not a real bank.*
