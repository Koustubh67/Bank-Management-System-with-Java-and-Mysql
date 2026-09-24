package com.koustubh.bank.service;

import com.koustubh.bank.dto.UploadedFile;
import com.koustubh.bank.exception.InvalidRequestException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Checks KYC uploads. The file type is decided from the file's first bytes, not from the name or the browser's
 * claim, so a renamed HTML or script file can never be stored and later shown to staff.
 */
public final class KycFiles {

    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private KycFiles() {
    }

    public static UploadedFile accept(MultipartFile file, String label) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Please upload your " + label);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidRequestException(label + " must be smaller than 2 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidRequestException("Could not read the " + label + " file. Please try again");
        }
        String type = detectType(bytes);
        if (type == null) {
            throw new InvalidRequestException(label + " must be a PDF, JPG or PNG file");
        }
        return new UploadedFile(safeName(file.getOriginalFilename(), type), type, bytes);
    }

    static String detectType(byte[] b) {
        if (b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return "application/pdf";
        }
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }

    private static String safeName(String original, String type) {
        // Keep only safe characters and drop leading dots/underscores (no hidden files or "../" tricks)
        String name = original == null ? "" : original.replaceAll("[^A-Za-z0-9._-]", "_").replaceFirst("^[._]+", "");
        if (name.isBlank()) {
            name = "document";
        }
        name = name.substring(0, Math.min(name.length(), 100));
        String ext = type.equals("application/pdf") ? ".pdf" : type.equals("image/png") ? ".png" : ".jpg";
        return name.toLowerCase().endsWith(ext) || (ext.equals(".jpg") && name.toLowerCase().endsWith(".jpeg"))
                ? name : name + ext;
    }
}
