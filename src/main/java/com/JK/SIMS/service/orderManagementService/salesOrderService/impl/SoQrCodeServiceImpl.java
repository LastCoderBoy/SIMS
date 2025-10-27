package com.JK.SIMS.service.orderManagementService.salesOrderService.impl;

import com.JK.SIMS.config.security.SecurityUtils;
import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.salesOrder.dtos.views.DetailedSalesOrderView;
import com.JK.SIMS.models.salesOrder.qrcode.SalesOrderQRCode;
import com.JK.SIMS.repository.salesOrderQrRepo.SalesOrderQrRepository;
import com.JK.SIMS.service.awsService.S3Service;
import com.JK.SIMS.service.orderManagementService.salesOrderService.SoQrCodeService;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import com.JK.SIMS.service.utilities.qrCode.QrCodeUtil;
import com.google.zxing.WriterException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class SoQrCodeServiceImpl implements SoQrCodeService {

    private final List<SalesOrderStatus> validUpdateStatusList = List.of(SalesOrderStatus.DELIVERY_IN_PROCESS, SalesOrderStatus.DELIVERED);
    private final Clock clock;

    private final QrCodeUtil qrCodeUtil;
    private final SalesOrderQrRepository salesOrderQrRepository;
    private final SecurityUtils securityUtils;
    private final S3Service s3Service;

    @Autowired
    public SoQrCodeServiceImpl(Clock clock, QrCodeUtil qrCodeUtil, SalesOrderQrRepository salesOrderQrRepository, SecurityUtils securityUtils, S3Service s3Service) {
        this.clock = clock;
        this.qrCodeUtil = qrCodeUtil;
        this.salesOrderQrRepository = salesOrderQrRepository;
        this.securityUtils = securityUtils;
        this.s3Service = s3Service;
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
    public ApiResponse<String> updateOrderStatus(String qrToken, String jwtToken, SalesOrderStatus newStatusValue, HttpServletRequest request) {
        try {
            if(newStatusValue == null){
                throw new ValidationException("Missing status value");
            }
            SalesOrderQRCode soQrEntity = getSoQrCodeByToken(qrToken);
            SalesOrder salesOrder = soQrEntity.getSalesOrder();
            if(!isOrderStatusCanBeUpdated(salesOrder, newStatusValue)){
                log.warn("SO-QR: updateOrderStatus() Order with ID {} cannot be updated to status {}!", salesOrder.getId(), newStatusValue);
                throw new ValidationException("Order cannot be updated to status: " + newStatusValue);
            }
            String username = securityUtils.validateAndExtractUsername(jwtToken);
            salesOrder.setStatus(newStatusValue);
            salesOrder.setUpdatedBy(username);
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
    public SalesOrderQRCode generateAndLinkQrCode(String orderReference) throws IOException, WriterException {
        String secureToken = GlobalServiceHelper.generateToken();
        String qrData = "http://localhost:8080/api/v1/products/manage-order/so/qr/" + secureToken;
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
     * Get temporary URL for accessing the QR code image
     * Call this when you need to display or share the QR code
     *
     * @param s3Key The S3 key from the QR code entity
     * @param duration How long the URL should be valid
     * @return Temporary pre-signed URL
     */
    @Override
    public String getQrCodeUrl(String s3Key, Duration duration) {
        return s3Service.generatePresignedUrl(s3Key, duration);
    }

    /**
     * Get QR code URL with default 7-day validity
     */
    public String getQrCodeUrl(String s3Key) {
        return getQrCodeUrl(s3Key, Duration.ofDays(7));
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

    @Transactional(readOnly = true)
    public SalesOrderQRCode getSoQrCodeByToken(String qrToken){
        return salesOrderQrRepository.findByToken(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("QR Code not found with token: " + qrToken));
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
            throw new ServiceException("Failed to log scanner details", e);
        }
    }

    private boolean isOrderStatusCanBeUpdated(SalesOrder salesOrder, SalesOrderStatus newStatusValue){
        SalesOrderStatus currentStatus = salesOrder.getStatus();
        if(currentStatus == SalesOrderStatus.APPROVED || currentStatus == SalesOrderStatus.DELIVERY_IN_PROCESS){
            return validUpdateStatusList.contains(newStatusValue);
        }
        return false;
    }
}
