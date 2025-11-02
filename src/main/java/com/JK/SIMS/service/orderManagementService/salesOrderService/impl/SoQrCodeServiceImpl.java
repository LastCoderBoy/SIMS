package com.JK.SIMS.service.orderManagementService.salesOrderService.impl;

import com.JK.SIMS.config.security.utils.SecurityUtils;
import com.JK.SIMS.exception.InvalidTokenException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.salesOrder.dtos.views.DetailedSalesOrderView;
import com.JK.SIMS.models.salesOrder.qrcode.SalesOrderQRCode;
import com.JK.SIMS.models.salesOrder.qrcode.dtos.QrCodeUrlResponse;
import com.JK.SIMS.repository.salesOrderQrRepo.SalesOrderQrRepository;
import com.JK.SIMS.repository.salesOrderRepo.SalesOrderRepository;
import com.JK.SIMS.service.awsService.S3Service;
import com.JK.SIMS.service.orderManagementService.salesOrderService.SoQrCodeService;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import com.JK.SIMS.service.utilities.qrCode.QrCodeUtil;
import com.google.zxing.WriterException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SoQrCodeServiceImpl implements SoQrCodeService {

    @Value( "${app.backend.base-url}")
    private String baseUrl;

    private final List<SalesOrderStatus> validUpdateStatusList = List.of(
            SalesOrderStatus.DELIVERY_IN_PROCESS, SalesOrderStatus.DELIVERED);

    private final Clock clock;
    private final QrCodeUtil qrCodeUtil;
    private final SecurityUtils securityUtils;
    private final S3Service s3Service;

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderQrRepository salesOrderQrRepository;

    @PostConstruct
    private void validateConfiguration() {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            log.error("SO-QR: app.backend.base-url is not configured!");
            throw new IllegalStateException("app.backend.base-url must be configured");
        }
        log.info("SO-QR: Configuration validated - Base URL: {}", baseUrl);
    }

    @Override
    @Transactional
    public DetailedSalesOrderView verifyQrCode(String qrToken, String jwtToken, HttpServletRequest request) {
        try {
            SalesOrderQRCode soQrEntity = getSoQrCodeByToken(qrToken);
            SalesOrder salesOrder = soQrEntity.getSalesOrder();

            // Log the scanner details and save the entity
            logScanner(soQrEntity, jwtToken, request);
            salesOrderQrRepository.save(soQrEntity);
            log.info("SO-QR: verifyQrCode() Returning detailed salesOrder view for ID Reference: {}", salesOrder.getOrderReference());
            return new DetailedSalesOrderView(salesOrder);
        } catch (ResourceNotFoundException rnfe){
            throw rnfe;
        } catch (Exception e) {
            log.error("SO-QR: verifyQrCode() Error verifying QR Code - {}", e.getMessage());
            throw new ServiceException("Failed to verify QR Code", e);
        }
    }

    @Override
    @Transactional
    public ApiResponse<String> updateOrderStatus(String qrToken, String jwtToken, SalesOrderStatus newStatusValue,
                                                 HttpServletRequest request) {
        try {
            SalesOrderQRCode soQrEntity = getSoQrCodeByToken(qrToken);
            SalesOrder salesOrder = soQrEntity.getSalesOrder();
            if(!isOrderStatusCanBeUpdated(salesOrder, newStatusValue)){
                log.warn("SO-QR: updateOrderStatus() Order with ID {} cannot be updated to status {}!", salesOrder.getId(), newStatusValue);
                throw new ValidationException("Order cannot be updated to status: " + newStatusValue);
            }
            String username = securityUtils.validateAndExtractUsername(jwtToken);
            salesOrder.setUpdatedBy(username);
            salesOrder.setStatus(newStatusValue);
            if(newStatusValue == SalesOrderStatus.DELIVERED){
                salesOrder.setDeliveryDate(GlobalServiceHelper.now(clock));
            }
            logScanner(soQrEntity, jwtToken, request);
            salesOrderQrRepository.save(soQrEntity);
            log.info("SO-QR: updateOrderStatus() Order with ID {} updated to status {}", salesOrder.getId(), newStatusValue);
            return new ApiResponse<>(true, "Order updated successfully");
        } catch (BadRequestException e) {
            log.error("SO-QR: Bad Request - {}", e.getMessage());
            throw new InvalidTokenException("Invalid Token provided. Please re-login.");
        } catch (ResourceNotFoundException rnfe){
            throw rnfe;
        } catch (Exception e) {
            log.error("SO-QR: updateOrderStatus() Error updating order status - {}", e.getMessage());
            throw new ServiceException("Failed to update order status", e);
        }
    }

    @Override
    public QrCodeUrlResponse getPresignedQrCodeUrl(Long salesOrderId){
        try {
            SalesOrder salesOrder = getSalesOrderById(salesOrderId); // might throw ResourceNotFoundException

            // Get the associated QR code entity and its S3 key
            SalesOrderQRCode soQrEntity = salesOrder.getQrCode();
            if (soQrEntity == null || soQrEntity.getQrCodeS3Key() == null) {
                throw new ResourceNotFoundException("QR Code not found for SalesOrder ID: " + salesOrderId);
            }
            String s3Key = soQrEntity.getQrCodeS3Key();
            String qrImageUrl = s3Service.generatePresignedUrl(s3Key, Duration.ofMinutes(5));

            // Create the QrResponse Entity
            LocalDateTime expiryTime = LocalDateTime.now(clock).plusMinutes(5);
            return new QrCodeUrlResponse(qrImageUrl, salesOrder.getOrderReference(), expiryTime);
        } catch (ResourceNotFoundException rnfe){
            throw rnfe;
        } catch (S3Exception e) {
            log.error("SO-QR: Failed to generate presigned URL for salesOrderId: {}", salesOrderId, e);
            throw new ServiceException("Failed to generate QR code URL", e);
        } catch (Exception e) {
            log.error("SO-QR: Unexpected error generating presigned URL for salesOrderId: {}", salesOrderId, e);
            throw new ServiceException("Unexpected error generating QR code URL", e);
        }
    }

    @Override
    public SalesOrderQRCode generateAndLinkQrCode(String orderReference) throws IOException, WriterException {
        String secureToken = GlobalServiceHelper.generateToken();
        String qrData = baseUrl + "/api/v1/products/manage-order/so/qrcode/" + secureToken;
        byte[] qrImageBytes = qrCodeUtil.generateQrCodeImage(qrData, 250, 250);

        // Define a unique object key (filename) for S3
        String s3Key = "qr-codes/" + orderReference + ".png";
        String uploadedS3Key = s3Service.uploadFile(s3Key, qrImageBytes, "image/png");

        // Populate the entity and return the object
        SalesOrderQRCode salesOrderQRCode = new SalesOrderQRCode();
        salesOrderQRCode.setQrCodeS3Key(uploadedS3Key);
        salesOrderQRCode.setQrToken(secureToken);
        return salesOrderQRCode; // the Cascade setting will automatically save the entity
    }

    /**
     * Delete QR code from S3
     */
    @Override
    public void deleteQrCodeFromS3(String s3Key) {
        try {
            s3Service.deleteFile(s3Key);
            log.info("Deleted QR code from S3: {}", s3Key);
        } catch (Exception e) {
            log.error("Failed to delete QR code from S3: {}", s3Key, e);
            throw e;
        }
    }

    private void logScanner(SalesOrderQRCode soQrEntity, String jwtToken, HttpServletRequest request){
        try {
            // Extract the scanner details
            String userAgent = request.getHeader("User-Agent");
            String ipAddress = securityUtils.extractClientIp(request);
            String username = securityUtils.validateAndExtractUsername(jwtToken);


            // Populate the entity
            soQrEntity.setLastScannedAt(GlobalServiceHelper.now(clock));
            soQrEntity.setScannedBy(username);
            soQrEntity.setIpAddress(ipAddress);
            soQrEntity.setUserAgent(userAgent);
        } catch (Exception e) {
            log.error("SO-QR: logScanner() Error logging scanner details - {}", e.getMessage());
        }
    }

    private boolean isOrderStatusCanBeUpdated(SalesOrder salesOrder, SalesOrderStatus newStatusValue){
        SalesOrderStatus currentStatus = salesOrder.getStatus();
        if(currentStatus == SalesOrderStatus.APPROVED || currentStatus == SalesOrderStatus.DELIVERY_IN_PROCESS){
            return validUpdateStatusList.contains(newStatusValue);
        }
        return false;
    }

    private SalesOrderQRCode getSoQrCodeByToken(String qrToken){
        return salesOrderQrRepository.findByToken(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("QR Code not found with token: " + qrToken));
    }

    private SalesOrder getSalesOrderById(Long orderId) {
        return salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder with ID: " + orderId + " not found"));
    }
}
