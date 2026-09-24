-- Fixed or floating interest. Floating loans are linked to the RBI repo rate: rate = repo rate + spread_percent.
-- Loans created before this migration were priced at a fixed rate.
ALTER TABLE loan ADD COLUMN rate_type VARCHAR(10) NOT NULL DEFAULT 'FIXED';
ALTER TABLE loan ADD COLUMN spread_percent DECIMAL(5, 2);

-- The repo rate floating loans follow. One row, locked by loan approvals and by repo rate changes.
CREATE TABLE repo_rate (
    id             BIGINT        NOT NULL PRIMARY KEY,
    rate_percent   DECIMAL(5, 2) NOT NULL,
    effective_from DATE          NOT NULL,
    updated_by     VARCHAR(50)   NOT NULL,
    updated_at     TIMESTAMP(6)  NOT NULL
);
-- RBI cut the repo rate to 5.25% on 5 Dec 2025 and kept it there at every 2026 policy review up to August 2026.
INSERT INTO repo_rate (id, rate_percent, effective_from, updated_by, updated_at)
VALUES (1, 5.25, '2025-12-05', 'system', CURRENT_TIMESTAMP);

CREATE TABLE repo_rate_change (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    old_rate        DECIMAL(5, 2),
    new_rate        DECIMAL(5, 2) NOT NULL,
    effective_from  DATE          NOT NULL,
    note            VARCHAR(200)  NOT NULL,
    changed_by      VARCHAR(50)   NOT NULL,
    loans_repriced  INT           NOT NULL,
    created_at      TIMESTAMP(6)  NOT NULL
);
INSERT INTO repo_rate_change (old_rate, new_rate, effective_from, note, changed_by, loans_repriced, created_at)
VALUES (5.50, 5.25, '2025-12-05', 'RBI Monetary Policy, 5 Dec 2025: repo rate cut by 0.25%', 'system', 0, CURRENT_TIMESTAMP);

-- Every reset of a floating-rate loan, shown to the customer and to staff.
CREATE TABLE loan_rate_change (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_id          BIGINT         NOT NULL,
    from_instalment  INT            NOT NULL,
    effective_from   DATE           NOT NULL,
    old_rate         DECIMAL(5, 2)  NOT NULL,
    new_rate         DECIMAL(5, 2)  NOT NULL,
    old_emi          DECIMAL(15, 2) NOT NULL,
    new_emi          DECIMAL(15, 2) NOT NULL,
    repo_rate        DECIMAL(5, 2)  NOT NULL,
    changed_by       VARCHAR(50)    NOT NULL,
    created_at       TIMESTAMP(6)   NOT NULL,
    CONSTRAINT fk_rate_change_loan FOREIGN KEY (loan_id) REFERENCES loan (id)
);
CREATE INDEX idx_rate_change_loan ON loan_rate_change (loan_id);
