package com.JK.SIMS.service.utilities.qrCode;

import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class QrCodeUtil {

    public byte[] generateQrCodeImage(String data, int width, int height) throws WriterException, IOException {
//        String secureToken = GlobalServiceHelper.generateToken();
//        String qrData = "http://localhost:8080/api/v1/products/manage-order/so/qr/" + secureToken;

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height);
        try (ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            return pngOutputStream.toByteArray();
        }
    }
}
