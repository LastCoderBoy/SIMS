package com.JK.SIMS.service.orderManagementService.salesOrderService.impl;

import com.JK.SIMS.config.security.SecurityUtils;
import com.JK.SIMS.config.security.TokenUtils;
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
import com.JK.SIMS.service.orderManagementService.salesOrderService.SoQrCodeService;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.time.Clock;
import java.util.List;

@Service
@Slf4j
public class SoQrCodeServiceImpl implements SoQrCodeService {

    private final List<SalesOrderStatus> validUpdateStatusList = List.of(SalesOrderStatus.DELIVERY_IN_PROCESS, SalesOrderStatus.DELIVERED);

    private final Clock clock;

    private final SalesOrderQrRepository salesOrderQrRepository;
    private final SecurityUtils securityUtils;

    @Autowired
    public SoQrCodeServiceImpl(Clock clock, SalesOrderQrRepository salesOrderQrRepository, SecurityUtils securityUtils) {
        this.clock = clock;
        this.salesOrderQrRepository = salesOrderQrRepository;
        this.securityUtils = securityUtils;
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
