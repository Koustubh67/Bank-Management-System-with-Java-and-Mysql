-- Every attempt to pay a loan EMI (card, UPI QR, savings account or auto-debit) and, once paid, its receipt.
-- Card and UPI payments are PENDING for up to 5 minutes. Only a card's network, last 4 digits and name are stored.
CREATE TABLE emi_payment (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference           VARCHAR(20)    NOT NULL,
    loan_id             BIGINT         NOT NULL,
    instalment_id       BIGINT         NOT NULL,
    instalment_number   INT            NOT NULL,
    amount              DECIMAL(15, 2) NOT NULL,
    method              VARCHAR(12)    NOT NULL,
    status              VARCHAR(10)    NOT NULL,
    paid_instalment_id  BIGINT,
    card_network        VARCHAR(20),
    card_last4          VARCHAR(4),
    card_holder         VARCHAR(80),
    otp_hash            VARCHAR(100),
    otp_attempts        INT            NOT NULL,
    transaction_id      VARCHAR(40),
    failure_reason      VARCHAR(200),
    created_at          TIMESTAMP(6)   NOT NULL,
    expires_at          TIMESTAMP(6),
    paid_at             TIMESTAMP(6),
    version             BIGINT         NOT NULL,
    CONSTRAINT uk_emi_payment_ref UNIQUE (reference),
    -- An EMI can have many failed or expired attempts but only one successful payment
    CONSTRAINT uk_emi_payment_paid_instalment UNIQUE (paid_instalment_id),
    CONSTRAINT fk_emi_payment_loan FOREIGN KEY (loan_id) REFERENCES loan (id),
    CONSTRAINT fk_emi_payment_instalment FOREIGN KEY (instalment_id) REFERENCES loan_instalment (id)
);
CREATE INDEX idx_emi_payment_loan ON emi_payment (loan_id);
CREATE INDEX idx_emi_payment_pending ON emi_payment (status, expires_at);

-- Receipts for EMIs paid before this version (they were all paid from the savings account)
INSERT INTO emi_payment (reference, loan_id, instalment_id, instalment_number, amount, method, status, paid_instalment_id,
                         otp_attempts, created_at, paid_at, version)
SELECT CONCAT('JBR', LPAD(i.id, 10, '0')), i.loan_id, i.id, i.number, i.emi, 'ACCOUNT', 'PAID', i.id, 0, i.paid_on, i.paid_on, 0
FROM loan_instalment i
WHERE i.status = 'PAID';
