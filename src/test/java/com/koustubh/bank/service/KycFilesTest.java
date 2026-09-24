package com.koustubh.bank.service;

import com.koustubh.bank.dto.UploadedFile;
import com.koustubh.bank.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KycFilesTest {

    @Test
    void detectsTypeFromTheFileContentNotTheName() {
        UploadedFile jpg = KycFiles.accept(new MockMultipartFile("f", "scan.pdf", "application/pdf",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01}), "PAN card");
        assertThat(jpg.contentType()).isEqualTo("image/jpeg");
        assertThat(jpg.fileName()).isEqualTo("scan.pdf.jpg");

        UploadedFile pdf = KycFiles.accept(new MockMultipartFile("f", "../../etc/Aadhaar card.PDF", "text/plain",
                "%PDF-1.7 hello".getBytes()), "Aadhaar card");
        assertThat(pdf.contentType()).isEqualTo("application/pdf");
        assertThat(pdf.fileName()).isEqualTo("etc_Aadhaar_card.PDF");
    }

    @Test
    void rejectsMissingDangerousAndOversizedFiles() {
        assertThatThrownBy(() -> KycFiles.accept(null, "PAN card")).hasMessage("Please upload your PAN card");
        assertThatThrownBy(() -> KycFiles.accept(new MockMultipartFile("f", "x.png", "image/png",
                "<script>alert(1)</script>".getBytes()), "PAN card"))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("PDF, JPG or PNG");
        assertThatThrownBy(() -> KycFiles.accept(new MockMultipartFile("f", "x.svg", "image/svg+xml",
                "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes()), "PAN card"))
                .hasMessageContaining("PDF, JPG or PNG");
        byte[] big = new byte[KycFiles.MAX_BYTES + 1];
        big[0] = '%'; big[1] = 'P'; big[2] = 'D'; big[3] = 'F';
        assertThatThrownBy(() -> KycFiles.accept(new MockMultipartFile("f", "big.pdf", "application/pdf", big), "PAN card"))
                .hasMessageContaining("2 MB");
    }
}
