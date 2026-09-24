package com.koustubh.bank.web;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Draws a QR code as inline SVG, so no image files or extra requests are needed. */
@Component
public class QrCodes {

    public String svg(String text) {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, Map.of(EncodeHintType.MARGIN, 1));
        } catch (WriterException e) {
            throw new IllegalArgumentException("Cannot encode QR code", e);
        }
        int w = matrix.getWidth();
        int h = matrix.getHeight();
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + w + " " + h
                + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"UPI QR code\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path fill=\"#0b2545\" d=\"" + path + "\"/></svg>";
    }
}
