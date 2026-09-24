-- Fixed deposits and SIPs a customer has opened from net banking.
CREATE TABLE investment (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id       BIGINT         NOT NULL,
    type             VARCHAR(20)    NOT NULL,
    reference        VARCHAR(20)    NOT NULL,
    fund             VARCHAR(20),
    amount           DECIMAL(15, 2) NOT NULL,
    rate_percent     DECIMAL(5, 2)  NOT NULL,
    tenure_months    INT,
    start_date       DATE           NOT NULL,
    maturity_date    DATE,
    maturity_amount  DECIMAL(15, 2),
    next_debit_date  DATE,
    instalments_paid INT            NOT NULL,
    created_at       TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_investment_reference UNIQUE (reference),
    CONSTRAINT fk_investment_account FOREIGN KEY (account_id) REFERENCES account (id)
);

-- Insurance policies bought from net banking. The first year's premium is debited from the account.
CREATE TABLE insurance_policy (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id     BIGINT         NOT NULL,
    plan           VARCHAR(20)    NOT NULL,
    policy_number  VARCHAR(20)    NOT NULL,
    cover          DECIMAL(15, 2) NOT NULL,
    annual_premium DECIMAL(15, 2) NOT NULL,
    details        VARCHAR(60),
    start_date     DATE           NOT NULL,
    end_date       DATE           NOT NULL,
    created_at     TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_policy_number UNIQUE (policy_number),
    CONSTRAINT fk_policy_account FOREIGN KEY (account_id) REFERENCES account (id)
);
