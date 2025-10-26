package com.JK.SIMS.models.salesOrder.qrcode;

import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

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

    @Column(nullable = false, unique = true)
    private String qrCodeUrl; // Full S3 URL (e.g. https://s3.amazonaws.com/bucket/...

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @Column
    private LocalDateTime lastScannedAt;

//    @Column
//    private String location;

    @Column(nullable = false, unique = true)
    private String qrToken; // unique secure token for identifying scans

    // ***** Scanner details *****
    @Column
    private String scannedBy; // username of the scanner (linked to SIMS user)

    @Column(length = 45)
    private String ipAddress; // last scanner IP

    @Column(length = 255)
    private String userAgent; // "Chrome on Windows" etc.

    // ***** Relationship detail *****
    @OneToOne(mappedBy = "qrCode")
    @JsonIgnore  // avoid infinite loop
    private SalesOrder salesOrder;

}
