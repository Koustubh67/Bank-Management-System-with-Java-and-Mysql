package com.koustubh.bank.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A scan or photo of a KYC document, checked by bank staff before approving the account. */
@Entity
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    private DocumentType docType;

    private String fileName;

    private String contentType;

    private int sizeBytes;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] content;

    private LocalDateTime uploadedAt;

    protected KycDocument() {
    }

    public KycDocument(Customer customer, DocumentType docType, String fileName, String contentType, byte[] content,
                       LocalDateTime uploadedAt) {
        this.customer = customer;
        this.docType = docType;
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
        this.sizeBytes = content.length;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public DocumentType getDocType() { return docType; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public int getSizeBytes() { return sizeBytes; }
    public byte[] getContent() { return content; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
