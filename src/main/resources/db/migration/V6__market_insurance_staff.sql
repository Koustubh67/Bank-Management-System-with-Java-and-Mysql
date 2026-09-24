-- Contact number for SMS alerts (new customers must give one; older rows can add it from their profile).
ALTER TABLE customer ADD COLUMN mobile VARCHAR(10);

-- Which staff member approved or declined an application, and when.
ALTER TABLE account ADD COLUMN reviewed_by VARCHAR(50);
ALTER TABLE account ADD COLUMN reviewed_at TIMESTAMP(6);

-- Staff accounts: admins can add officers; new staff must change their temporary password on first login.
ALTER TABLE admin_user ADD COLUMN full_name VARCHAR(80);
ALTER TABLE admin_user ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'ADMIN';
ALTER TABLE admin_user ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE admin_user ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE admin_user ADD COLUMN created_at TIMESTAMP(6);
UPDATE admin_user SET full_name = 'Branch Manager' WHERE full_name IS NULL;

-- Mutual funds now use real schemes (AMFI scheme codes, NAVs from mfapi.in) and track units bought.
ALTER TABLE investment ADD COLUMN scheme_code BIGINT;
ALTER TABLE investment ADD COLUMN scheme_name VARCHAR(150);
ALTER TABLE investment ADD COLUMN units DECIMAL(18, 4);
ALTER TABLE investment ADD COLUMN payment_method VARCHAR(10);
UPDATE investment SET scheme_code = 120716, scheme_name = 'UTI Nifty 50 Index Fund - Direct Plan - Growth' WHERE fund = 'NIFTY_INDEX';
UPDATE investment SET scheme_code = 122639, scheme_name = 'Parag Parikh Flexi Cap Fund - Direct Plan - Growth' WHERE fund = 'FLEXI_CAP';
UPDATE investment SET scheme_code = 119091, scheme_name = 'HDFC Liquid Fund - Direct Plan - Growth Option' WHERE fund = 'LIQUID';
ALTER TABLE investment DROP COLUMN fund;

-- Insurance is sold through an expert callback: the customer asks, staff call back and then issue the policy.
ALTER TABLE insurance_policy ADD COLUMN insurer VARCHAR(80);
CREATE TABLE insurance_request (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id     BIGINT        NOT NULL,
    plan           VARCHAR(20)   NOT NULL,
    reference      VARCHAR(20)   NOT NULL,
    cover_wanted   BIGINT        NOT NULL,
    contact_name   VARCHAR(80)   NOT NULL,
    mobile         VARCHAR(10)   NOT NULL,
    city           VARCHAR(50)   NOT NULL,
    age            INT           NOT NULL,
    extra          VARCHAR(300),
    preferred_time VARCHAR(20)   NOT NULL,
    status         VARCHAR(15)   NOT NULL,
    staff_note     VARCHAR(300),
    handled_by     VARCHAR(50),
    policy_id      BIGINT,
    created_at     TIMESTAMP(6)  NOT NULL,
    updated_at     TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_insurance_request_ref UNIQUE (reference),
    CONSTRAINT fk_insurance_request_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT fk_insurance_request_policy FOREIGN KEY (policy_id) REFERENCES insurance_policy (id)
);

-- Outbox of SMS and email alerts sent to customers (simulated: written here and to the log).
CREATE TABLE notification (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT       NOT NULL,
    channel     VARCHAR(5)   NOT NULL,
    destination VARCHAR(100) NOT NULL,
    subject     VARCHAR(120) NOT NULL,
    message     VARCHAR(500) NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_notification_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
);
CREATE INDEX idx_notification_customer ON notification (customer_id, created_at);
