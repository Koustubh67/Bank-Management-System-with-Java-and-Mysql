-- Net banking login: every customer gets a Customer ID and their own password.
ALTER TABLE customer ADD COLUMN customer_id VARCHAR(12);
-- Customers created before this change get an ID from their row id; they set a password with their debit card.
UPDATE customer SET customer_id = CONCAT('JB', LPAD(id, 8, '0'));
ALTER TABLE customer ADD CONSTRAINT uk_customer_customer_id UNIQUE (customer_id);
ALTER TABLE customer ADD COLUMN password_hash VARCHAR(100);
ALTER TABLE customer ADD COLUMN failed_logins INT NOT NULL DEFAULT 0;
ALTER TABLE customer ADD COLUMN login_locked BOOLEAN NOT NULL DEFAULT FALSE;
