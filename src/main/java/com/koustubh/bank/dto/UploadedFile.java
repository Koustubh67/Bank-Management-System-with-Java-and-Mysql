package com.koustubh.bank.dto;

import java.io.Serializable;

/** A checked upload kept in the signup session until the application is submitted. */
public record UploadedFile(String fileName, String contentType, byte[] content) implements Serializable {

    public String getSizeLabel() {
        return (content.length / 1024 + 1) + " KB";
    }
}
