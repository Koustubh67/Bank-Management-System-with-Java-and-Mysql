package com.koustubh.bank.repository;

import com.koustubh.bank.domain.DocumentType;

import java.time.LocalDateTime;

/** KYC document details without the file bytes, for listing on the staff account page. */
public record DocumentInfo(Long id, DocumentType docType, String fileName, String contentType, int sizeBytes,
                           LocalDateTime uploadedAt) {

    public boolean isImage() {
        return contentType.startsWith("image/");
    }

    public String getSizeLabel() {
        return sizeBytes < 1024 * 1024 ? (sizeBytes / 1024 + 1) + " KB"
                : String.format("%.1f MB", sizeBytes / (1024.0 * 1024));
    }
}
