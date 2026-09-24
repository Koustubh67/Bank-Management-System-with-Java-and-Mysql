-- Loans applied for by customers from net banking. After approval the loan is disbursed to the customer's
-- account and repaid by monthly EMIs (loan_instalment rows, one per month, generated at approval).
CREATE TABLE loan (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id           BIGINT         NOT NULL,
    type                 VARCHAR(15)    NOT NULL,
    reference            VARCHAR(20)    NOT NULL,
    amount_requested     DECIMAL(15, 2) NOT NULL,
    months_requested     INT            NOT NULL,
    purpose              VARCHAR(200)   NOT NULL,
    employment           VARCHAR(30)    NOT NULL,
    monthly_income       DECIMAL(15, 2) NOT NULL,
    status               VARCHAR(10)    NOT NULL,
    principal            DECIMAL(15, 2),
    rate_percent         DECIMAL(5, 2),
    tenure_months        INT,
    emi                  DECIMAL(15, 2),
    disbursed_on         DATE,
    first_emi_date       DATE,
    end_date             DATE,
    outstanding          DECIMAL(15, 2),
    reviewed_by          VARCHAR(50),
    reviewed_at          TIMESTAMP(6),
    reject_reason        VARCHAR(200),
    created_at           TIMESTAMP(6)   NOT NULL,
    version              BIGINT         NOT NULL,
    CONSTRAINT uk_loan_reference UNIQUE (reference),
    CONSTRAINT fk_loan_account FOREIGN KEY (account_id) REFERENCES account (id)
);

CREATE TABLE loan_instalment (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_id        BIGINT         NOT NULL,
    number         INT            NOT NULL,
    due_date       DATE           NOT NULL,
    emi            DECIMAL(15, 2) NOT NULL,
    principal_part DECIMAL(15, 2) NOT NULL,
    interest_part  DECIMAL(15, 2) NOT NULL,
    balance_after  DECIMAL(15, 2) NOT NULL,
    status         VARCHAR(10)    NOT NULL,
    paid_on        TIMESTAMP(6),
    CONSTRAINT uk_instalment_number UNIQUE (loan_id, number),
    CONSTRAINT fk_instalment_loan FOREIGN KEY (loan_id) REFERENCES loan (id)
);
CREATE INDEX idx_instalment_due ON loan_instalment (status, due_date);

-- Enquiries from the public Loans page (people who aren't customers yet). Staff call them back.
CREATE TABLE loan_enquiry (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference       VARCHAR(20)    NOT NULL,
    name            VARCHAR(80)    NOT NULL,
    mobile          VARCHAR(10)    NOT NULL,
    email           VARCHAR(100)   NOT NULL,
    city            VARCHAR(50)    NOT NULL,
    type            VARCHAR(15)    NOT NULL,
    amount          DECIMAL(15, 2) NOT NULL,
    employment      VARCHAR(30)    NOT NULL,
    monthly_income  DECIMAL(15, 2) NOT NULL,
    preferred_time  VARCHAR(20)    NOT NULL,
    status          VARCHAR(10)    NOT NULL,
    staff_note      VARCHAR(300),
    handled_by      VARCHAR(50),
    created_at      TIMESTAMP(6)   NOT NULL,
    updated_at      TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_loan_enquiry_ref UNIQUE (reference)
);
