CREATE TABLE customer (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name        VARCHAR(60)  NOT NULL,
    father_name      VARCHAR(60)  NOT NULL,
    date_of_birth    DATE         NOT NULL,
    gender           VARCHAR(10)  NOT NULL,
    email            VARCHAR(100) NOT NULL,
    marital_status   VARCHAR(20)  NOT NULL,
    address          VARCHAR(200) NOT NULL,
    city             VARCHAR(50)  NOT NULL,
    state            VARCHAR(50)  NOT NULL,
    pincode          VARCHAR(6)   NOT NULL,
    country          VARCHAR(50)  NOT NULL,
    religion         VARCHAR(20)  NOT NULL,
    category         VARCHAR(20)  NOT NULL,
    income           VARCHAR(30)  NOT NULL,
    education        VARCHAR(30)  NOT NULL,
    occupation       VARCHAR(30)  NOT NULL,
    pan              VARCHAR(10)  NOT NULL,
    aadhaar          VARCHAR(12)  NOT NULL,
    senior_citizen   BOOLEAN      NOT NULL,
    existing_account BOOLEAN      NOT NULL,
    created_at       TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_customer_pan UNIQUE (pan),
    CONSTRAINT uk_customer_aadhaar UNIQUE (aadhaar)
);

CREATE TABLE account (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_number VARCHAR(12)    NOT NULL,
    customer_id    BIGINT         NOT NULL,
    account_type   VARCHAR(20)    NOT NULL,
    status         VARCHAR(10)    NOT NULL,
    balance        DECIMAL(15, 2) NOT NULL,
    services       VARCHAR(255),
    version        BIGINT         NOT NULL,
    created_at     TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_account_number UNIQUE (account_number),
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
);

CREATE TABLE card (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_number     VARCHAR(16) NOT NULL,
    account_id      BIGINT      NOT NULL,
    pin_hash        VARCHAR(100) NOT NULL,
    failed_attempts INT         NOT NULL,
    blocked         BOOLEAN     NOT NULL,
    CONSTRAINT uk_card_number UNIQUE (card_number),
    CONSTRAINT uk_card_account UNIQUE (account_id),
    CONSTRAINT fk_card_account FOREIGN KEY (account_id) REFERENCES account (id)
);

-- Append-only ledger: rows are never updated or deleted.
CREATE TABLE transactions (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id           BIGINT         NOT NULL,
    type                 VARCHAR(20)    NOT NULL,
    amount               DECIMAL(15, 2) NOT NULL,
    balance_after        DECIMAL(15, 2) NOT NULL,
    reference_id         VARCHAR(36)    NOT NULL,
    counterparty_account VARCHAR(12),
    created_at           TIMESTAMP(6)   NOT NULL,
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES account (id)
);

CREATE INDEX idx_transactions_account_time ON transactions (account_id, created_at);

CREATE TABLE admin_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    CONSTRAINT uk_admin_username UNIQUE (username)
);
