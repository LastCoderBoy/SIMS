package com.JK.SIMS.models.salesOrder.qrcode;

import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.service.awsService.S3Service;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "sales_order_qr_codes")
public class SalesOrderQRCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "qr_token", unique = true, nullable = false, length = 100)
    private String qrToken; // unique secure token for identifying scans

    @Column(name = "qr_code_s3_key", nullable = false, length = 500)
    private String qrCodeS3Key; // S3 key for the QR code image

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @Column(name = "last_scanned_at")
    private LocalDateTime lastScannedAt; // When the QR code was last scanned

    // ***** Scanner details *****
    @Column
    private String scannedBy; // username of the scanner (linked to SIMS user)

    @Column(length = 45)
    private String ipAddress; // last scanner IP

    @Column(length = 255)
    private String userAgent; // "Chrome on Windows" etc.

    // ***** Relationship detail *****
    @OneToOne(mappedBy = "qrCode")
    @JsonIgnore  // Avoid infinite loop in JSON serialization
    private SalesOrder salesOrder;

}
