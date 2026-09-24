package com.koustubh.bank.repository;

import com.koustubh.bank.domain.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {

    @Query("""
            select new com.koustubh.bank.repository.DocumentInfo(
                d.id, d.docType, d.fileName, d.contentType, d.sizeBytes, d.uploadedAt)
            from KycDocument d where d.customer.id = :customerId order by d.docType
            """)
    List<DocumentInfo> findInfoByCustomerId(Long customerId);
}
