-- Why a pending application was declined, shown to the customer on the "Track application" page.
ALTER TABLE account ADD COLUMN decline_reason VARCHAR(200);

-- KYC documents (PAN and Aadhaar card scans) uploaded with the application. Stored in the database so the
-- app needs no file storage when deployed.
CREATE TABLE kyc_document (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id  BIGINT       NOT NULL,
    doc_type     VARCHAR(10)  NOT NULL,
    file_name    VARCHAR(120) NOT NULL,
    content_type VARCHAR(40)  NOT NULL,
    size_bytes   INT          NOT NULL,
    content      LONGBLOB     NOT NULL,
    uploaded_at  TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_kyc_customer_type UNIQUE (customer_id, doc_type),
    CONSTRAINT fk_kyc_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
);
