-- Free-text narration shown on statements, e.g. "UPI/priya.3311@javabank/lunch".
ALTER TABLE transactions ADD COLUMN remarks VARCHAR(120);

-- A UPI ID (virtual payment address) linked to one account, protected by its own 6-digit UPI PIN.
CREATE TABLE upi_handle (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    vpa             VARCHAR(50)  NOT NULL,
    account_id      BIGINT       NOT NULL,
    pin_hash        VARCHAR(100) NOT NULL,
    failed_attempts INT          NOT NULL,
    blocked         BOOLEAN      NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_upi_vpa UNIQUE (vpa),
    CONSTRAINT uk_upi_account UNIQUE (account_id),
    CONSTRAINT fk_upi_account FOREIGN KEY (account_id) REFERENCES account (id)
);
