package com.JK.SIMS.service.orderManagementService.salesOrderService.impl;

import com.JK.SIMS.config.security.utils.SecurityUtils;
import com.JK.SIMS.exception.*;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.SalesOrderAdjustments;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.salesOrder.dtos.SalesOrderRequestDto;
import com.JK.SIMS.models.salesOrder.dtos.views.DetailedSalesOrderView;
import com.JK.SIMS.models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.salesOrder.orderItem.dtos.BulkOrderItemsRequestDto;
import com.JK.SIMS.models.salesOrder.orderItem.dtos.OrderItemRequest;
import com.JK.SIMS.models.salesOrder.qrcode.SalesOrderQRCode;
import com.JK.SIMS.repository.salesOrderRepo.SalesOrderRepository;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.StockManagementLogic;
import com.JK.SIMS.service.orderManagementService.salesOrderService.SalesOrderService;
import com.JK.SIMS.service.orderManagementService.salesOrderService.SoQrCodeService;
import com.JK.SIMS.service.productManagementService.queryService.ProductQueryService;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import com.JK.SIMS.service.utilities.SalesOrderServiceHelper;
import com.JK.SIMS.service.utilities.salesOrderFilterLogic.SoFilterStrategy;
import com.JK.SIMS.service.utilities.salesOrderSearchLogic.SoSearchStrategy;
import jakarta.annotation.Nullable;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SalesOrderServiceImpl implements SalesOrderService {

    // ========== Helpers & Utilities ==========
    private final GlobalServiceHelper globalServiceHelper;
    private final SalesOrderServiceHelper salesOrderServiceHelper;
    private final SecurityUtils securityUtils;

    // ========== Components ==========
    private final StockManagementLogic stockManagementLogic;
    private final SoSearchStrategy omSoSearchStrategy;
    private final SoFilterStrategy filterSalesOrdersInOm;

    // ========== Services ==========
    private final ProductQueryService productQueryService;
    private final SoQrCodeService soQrCodeService;

    // ========== Repositories ==========
    private final SalesOrderRepository salesOrderRepository;




    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> getAllSummarySalesOrders(String sortBy, String sortDirection, int page, int size) {
        try {
            Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDirection);
            Page<SalesOrder> salesOrderPage = salesOrderRepository.findAll(pageable);
            log.info("OM-SO (getAllSummarySalesOrders): Returning {} paginated data", salesOrderPage.getContent().size());
            return salesOrderServiceHelper.transformToSummarySalesOrderView(salesOrderPage);
        } catch (DataAccessException da){
            log.error("OM-SO (getAllSummarySalesOrders): Database error occurred: {}", da.getMessage(), da);
            throw new DatabaseException("Database error occurred, please contact the administration");
        } catch (PropertyReferenceException e) {
            log.error("OM-SO (getAllSummarySalesOrders): Invalid sort field provided: {}", e.getMessage(), e);
            throw new ValidationException("Invalid sort field provided. Check your request");
        } catch (Exception e) {
            log.error("OM-SO (getAllSummarySalesOrders): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred: ", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DetailedSalesOrderView getDetailsForSalesOrderId(Long orderId) {
        try {
            globalServiceHelper.validateOrderId(orderId, salesOrderRepository, "SalesOrder"); // might throw ValidationException
            SalesOrder salesOrder = getSalesOrderById(orderId);
            log.info("Returning detailed salesOrder view for ID: {}", orderId);
            return new DetailedSalesOrderView(salesOrder);
        } catch (ValidationException | ResourceNotFoundException e) {
            throw e;
        } catch (DataAccessException da) {
            log.error("OM-SO (getDetailsForSalesOrderId): Database error occurred: {}", da.getMessage(), da);
            throw new DatabaseException("Database error occurred, please contact the administration");
        } catch (Exception e) {
            log.error("OM-SO (getDetailsForSalesOrderId): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred: ", e);
        }
    }


    @Override
    @Transactional
    public ApiResponse<String> createSalesOrder(@Valid SalesOrderRequestDto salesOrderRequestDto, String jwtToken) {
        List<OrderItem> reservedItems = new ArrayList<>();
        String qrCodeS3Key = null; // Track S3 key for rollback
        boolean success = false;
        try {
            // Validate the request and Create the Entity
            salesOrderServiceHelper.validateSalesOrderItems(salesOrderRequestDto.getOrderItems());
            String createdPerson = securityUtils.validateAndExtractUsername(jwtToken);
            String orderReference = generateOrderReference(LocalDate.now());

            // Create the QR Code and upload to the AWS S3 bucket
            SalesOrderQRCode salesOrderQRCode = soQrCodeService.generateAndLinkQrCode(orderReference);
            qrCodeS3Key = "qr-codes/" + orderReference + ".png"; // Tracking


            // Create the SalesOrder entity and link the QR Code
            SalesOrder salesOrder = createSalesOrderEntity(
                    salesOrderRequestDto,
                    orderReference,
                    createdPerson,
                    salesOrderQRCode
            );

            // Process each item with stock reservation
            populateSalesOrderWithItems(salesOrder, salesOrderRequestDto.getOrderItems());
            reservedItems.addAll(salesOrder.getItems()); // Track reserved items

            salesOrderRepository.save(salesOrder); // This still might fail
            success = true;
            log.info("OM-SO createSalesOrder(): SalesOrder created successfully with reference ID: {}", orderReference);
            return new ApiResponse<>(true, "SalesOrder created successfully and it is under PENDING status");
        } catch (ValidationException | InsufficientStockException | ResourceNotFoundException e) {
            log.error("OM-SO createSalesOrder(): Validation error - {}", e.getMessage());
            throw e;
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                log.warn("SalesOrder reference collision detected");
            }
            log.error("OM-SO createSalesOrder(): Data integrity error - {}", e.getMessage());
            throw new DatabaseException("Failed to save the order due to data integrity violation", e);
        } catch (DataAccessException dae) {
            log.error("OM-SO createSalesOrder(): Database error - {}", dae.getMessage());
            throw new DatabaseException("OM-SO createSalesOrder(): Failed to save the order to the database.", dae);
        } catch (Exception e) {
            log.error("OM-SO createSalesOrder(): Unexpected error - {}", e.getMessage());
            throw new ServiceException("Internal Service error occurred while creating the order.", e);
        } finally {
            // Any other unexpected error - rollback reservations
            if (!success) {
                // Rollback stock reservations
                if (!reservedItems.isEmpty()) {
                    rollbackReservations(reservedItems);
                }
                // Rollback S3 upload
                if (qrCodeS3Key != null) {
                    rollbackS3Upload(qrCodeS3Key);
                }
            }
        }
    }

    private void rollbackS3Upload(String s3Key) {
        try {
            soQrCodeService.deleteQrCodeFromS3(s3Key);
            log.info("Successfully rolled back S3 upload for key: {}", s3Key);
        } catch (Exception e) {
            log.error("Failed to rollback S3 upload for key {}: {}", s3Key, e.getMessage());
        }
    }

    private SalesOrder createSalesOrderEntity(SalesOrderRequestDto salesOrderRequestDto, String orderReference,
                                              String createdPerson, SalesOrderQRCode salesOrderQRCode) {
        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setOrderReference(orderReference);
        salesOrder.setDestination(salesOrderRequestDto.getDestination().trim());
        salesOrder.setStatus(SalesOrderStatus.PENDING);
        salesOrder.setCreatedBy(createdPerson);
        salesOrder.setCustomerName(salesOrderRequestDto.getCustomerName().trim());

        // Calculate estimated delivery date (7 business days)
        LocalDateTime estimatedDelivery = LocalDateTime.now().plusDays(7);
        salesOrder.setEstimatedDeliveryDate(estimatedDelivery);

        // Link the QR Code to the SalesOrder (will save the QR Code entity as well)
        salesOrder.setQrCode(salesOrderQRCode);

        // Initialize items to avoid null pointer exception
        salesOrder.setItems(new ArrayList<>());

        log.debug("OM-SO createSalesOrderEntity(): Created salesOrder entity with reference {}", orderReference);
        return salesOrder;
    }

    private void rollbackReservations(List<OrderItem> reservedItems) {
        for (OrderItem item : reservedItems) {
            try {
                stockManagementLogic.releaseReservation(
                        item.getProduct().getProductID(),
                        item.getQuantity()
                );
                log.debug("Rolled back reservation for product: {}", item.getProduct().getProductID());
            } catch (Exception rollbackEx) {
                log.error("Failed to rollback reservation for product {}: {}",
                        item.getProduct().getProductID(), rollbackEx.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public ApiResponse<String> updateSalesOrder(Long orderId, SalesOrderRequestDto salesOrderRequestDto, String jwtToken) {
        try {
            SalesOrder orderToBeUpdated = getSalesOrderById(orderId); // might throw ResourceNotFoundException
            if (orderToBeUpdated.isFinalized()) {
                log.warn("OM-SO updateSalesOrder(): Order with ID {} already processed, cannot be updated!", orderId);
                throw new ValidationException("Order is already processed, cannot be updated!");
            }
            salesOrderServiceHelper.validateSoRequestForUpdate(salesOrderRequestDto); // dto null check

            // Update the base fields
            updateBaseFieldsIfProvided(
                    orderToBeUpdated,
                    salesOrderRequestDto.getDestination(),
                    salesOrderRequestDto.getCustomerName());

            // Update the quantity
            updateItemQuantity(orderToBeUpdated, salesOrderRequestDto.getOrderItems());

            String updatedUser = securityUtils.validateAndExtractUsername(jwtToken);
            orderToBeUpdated.setUpdatedBy(updatedUser);
            salesOrderRepository.save(orderToBeUpdated);
            return new ApiResponse<>(true, "Sales Order: " + orderToBeUpdated.getOrderReference() + " is updated successfully.");
        } catch (ResourceNotFoundException | ValidationException | InsufficientStockException e) {
            throw e;
        } catch (Exception e) {
            log.error("OM-SO updateSalesOrder(): Unexpected error - {}", e.getMessage());
            throw new ServiceException("Internal Service error occurred while updating the order.", e);
        }
    }

    private void updateBaseFieldsIfProvided(SalesOrder orderToBeUpdated, @Nullable String destination, @Nullable String customerName) {
        Optional.ofNullable(destination).ifPresent(dest -> orderToBeUpdated.setDestination(dest.trim()));
        Optional.ofNullable(customerName).ifPresent(name -> orderToBeUpdated.setCustomerName(name.trim()));
    }

    private void updateItemQuantity(SalesOrder salesOrder, List<OrderItemRequest> orderItemsDto) {
        log.debug("List {}", orderItemsDto); // debug
        if (orderItemsDto == null || orderItemsDto.isEmpty()) {
            return; // Nothing to update
        }
        // Create map of existing items for quick lookup
        Map<String, OrderItem> existingItemsMap = salesOrder.getItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getProduct().getProductID(),
                        item -> item
                ));
        log.debug("Map {}", existingItemsMap);
        // Track stock adjustments for rollback
        List<SalesOrderAdjustments> stockAdjustments = new ArrayList<>();
        try {
            for(OrderItemRequest itemDto : orderItemsDto) {
                String productId = itemDto.getProductId();
                if(existingItemsMap.containsKey(productId)){
                    OrderItem existingItem = existingItemsMap.get(productId);
                    int quantityDifference = itemDto.getQuantity() - existingItem.getQuantity();
                    if (quantityDifference == 0) {
                        continue; // No change needed
                    }
                    if(quantityDifference > 0){
                        // Need to reserve more stock
                        stockManagementLogic.reserveStock(productId, quantityDifference);
                        stockAdjustments.add(new SalesOrderAdjustments(productId, quantityDifference, true));
                    } else {
                        // Need to release the excess stock
                        stockManagementLogic.releaseReservation(productId, Math.abs(quantityDifference));
                        stockAdjustments.add(new SalesOrderAdjustments(productId, quantityDifference, false));
                    }
                    existingItem.setQuantity(itemDto.getQuantity());
                    existingItem.setOrderPrice(existingItem.getProduct().getPrice()
                            .multiply(BigDecimal.valueOf(itemDto.getQuantity())));
                }
            }
            log.info("OM-SO updateOrderQuantity(): Successfully updated order items for order {}",
                    salesOrder.getOrderReference());
        } catch (Exception e) {
            // Rollback stock adjustments in reverse order
            log.warn("OM-SO updateItemQuantities(): Error occurred, rolling back {} stock adjustments",
                    stockAdjustments.size());

            Collections.reverse(stockAdjustments);
            for (SalesOrderAdjustments adjustment : stockAdjustments) {
                try {
                    if (adjustment.isWasReserved()) {
                        // Rollback reservation by releasing
                        stockManagementLogic.releaseReservation(adjustment.getProductId(), adjustment.getQuantity());
                    } else {
                        // Rollback release by reserving back
                        stockManagementLogic.reserveStock(adjustment.getProductId(), adjustment.getQuantity());
                    }
                } catch (Exception rollbackException) {
                    log.error("OM-SO updateItemQuantities(): Failed to rollback adjustment for product {}: {}",
                            adjustment.getProductId(), rollbackException.getMessage());
                }
            }
            log.error("OM-SO updateOrderQuantity(): Failed to update order items - {}", e.getMessage());
            throw e;
        }
    }

    @Transactional
    @Override
    public ApiResponse<String> addItemsToSalesOrder(Long orderId,
                                                    @Valid BulkOrderItemsRequestDto bulkOrderItemsRequestDto,
                                                    String jwtToken){
        List<OrderItem> newlyAddedItems = new ArrayList<>();
        boolean success = false;
        try {
            SalesOrder salesOrder = getSalesOrderById(orderId);
            if (salesOrder.isFinalized()) {
                log.warn("OM-SO addItemsToSalesOrder(): Order with ID {} already processed, cannot be updated!", orderId);
                throw new ValidationException("Order is already processed, cannot be updated!");
            }
            salesOrderServiceHelper.validateSalesOrderItems(bulkOrderItemsRequestDto.getOrderItems());
            String updatedUser = securityUtils.validateAndExtractUsername(jwtToken);

            // Keep track of how many items existed before adding new ones
            int initialItemCount = salesOrder.getItems().size();

            // Process and add items with stock reservation
            populateSalesOrderWithItems(salesOrder, bulkOrderItemsRequestDto.getOrderItems());

            // Identify only the newly added items for potential rollback
            if (salesOrder.getItems().size() > initialItemCount) {
                newlyAddedItems.addAll(salesOrder.getItems().subList(initialItemCount, salesOrder.getItems().size()));
            }

            salesOrder.setUpdatedBy(updatedUser);
            salesOrderRepository.save(salesOrder);
            success = true;
            log.info("OM-SO addItemsToSalesOrder(): New Items added to Sales Order: {}", salesOrder.getOrderReference());
            return new ApiResponse<>(true, "Order Item(s) added successfully");

        } catch (ValidationException | InsufficientStockException | ResourceNotFoundException e) {
            log.error("OM-SO addItemsToSalesOrder(): Error - {}", e.getMessage());
            throw e;
        } catch (DataAccessException dae) {
            log.error("OM-SO addItemsToSalesOrder(): Database error - {}", dae.getMessage());
            throw new DatabaseException("OM-SO addItemsToSalesOrder(): Failed to add items to the order.", dae);
        } catch (Exception e) {
            log.error("OM-SO addItemsToSalesOrder(): Unexpected error - {}", e.getMessage());
            throw new ServiceException("Internal Service error occurred while adding items to the order.", e);
        } finally {
            // This is the crucial part: if the operation failed, clean up the reservations
            if (!success && !newlyAddedItems.isEmpty()) {
                log.warn("Operation failed. Rolling back reservations for newly added items to order ID: {}", orderId);
                rollbackReservations(newlyAddedItems);
            }
        }
    }

    /**
     * Populates SalesOrder with OrderItems after reserving stock.
     * This method is NOT marked as @Transactional because it's always called
     * from a method that already has an active transaction.
     *
     * @param salesOrder The sales order to populate
     * @param orderItemRequestList List of order items to add
     * @throws ValidationException if product is not active
     * @throws InsufficientStockException if stock is insufficient
     * @throws ResourceNotFoundException if product/inventory not found
     */
    private void populateSalesOrderWithItems(SalesOrder salesOrder, List<OrderItemRequest> orderItemRequestList){
        for (OrderItemRequest itemDto : orderItemRequestList) {
            ProductsForPM product = productQueryService.findById(itemDto.getProductId());

            // Validate product status
            if (product.getStatus() != ProductStatus.ACTIVE && product.getStatus() != ProductStatus.ON_ORDER) {
                throw new ValidationException("Product '" + product.getName() + " ID: " + product.getProductID() + " is not active.");
            }

            // Reserve stock - throws exception if insufficient
            stockManagementLogic.reserveStock(product.getProductID(), itemDto.getQuantity());

            // Create and add OrderItem
            OrderItem orderItem = createOrderItem(product, itemDto.getQuantity());
            salesOrder.addOrderItem(orderItem);
        }
    }

    @Override
    @Transactional
    public ApiResponse<String> removeItemFromSalesOrder(Long orderId, Long itemId, String jwtToken){
        try {
            SalesOrder salesOrder = getSalesOrderById(orderId);
            if (salesOrder.isFinalized()) {
                log.warn("OM-SO removeItemFromSalesOrder(): Order ID {} finalized, cannot remove item!", orderId);
                throw new ValidationException("Order is finalized, cannot remove item!");
            }
            String updatedBy = securityUtils.validateAndExtractUsername(jwtToken);
            salesOrder.setUpdatedBy(updatedBy);

            OrderItem itemToRemove = salesOrder.getItems().stream()
                    .filter(item -> item.getId().equals(itemId))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Item ID " + itemId + " not found in order " + salesOrder.getOrderReference()));

            if(itemToRemove.isFinalized()){
                log.warn("OM-SO removeItemFromSalesOrder(): Item {} finalized, cannot remove!", itemId);
                throw new ValidationException("Order Item is finalized, cannot remove!");
            }

            stockManagementLogic.releaseReservation(itemToRemove.getProduct().getProductID(), itemToRemove.getQuantity());
            salesOrder.removeOrderItem(itemToRemove);
            // After removing update the status
            salesOrderServiceHelper.updateSoStatusBasedOnItemQuantity(salesOrder);
            salesOrderRepository.save(salesOrder);
            log.info("OM-SO removeItemFromSalesOrder(): Item {} removed from Sales Order {}", itemId, salesOrder.getOrderReference());
            return new ApiResponse<>(true, "Item removed successfully");
        } catch (ResourceNotFoundException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("OM-SO removeItemFromSalesOrder(): Unexpected error - {}", e.getMessage());
            throw new ServiceException("Internal error removing item", e);
        }
    }

    private OrderItem createOrderItem(ProductsForPM product, Integer orderedQuantity) {
        if (product == null || orderedQuantity == null || orderedQuantity <= 0) {
            throw new ValidationException("Invalid product or quantity for order item");
        }
        BigDecimal unitPriceAtOrder = product.getPrice();
        BigDecimal totalPrice = unitPriceAtOrder.multiply(BigDecimal.valueOf(orderedQuantity));
        return new OrderItem(orderedQuantity, product, totalPrice);
    }


    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> searchInSalesOrders(String text, int page, int size, String sortBy, String sortDirection) {
        try {
            globalServiceHelper.validatePaginationParameters(page, size);
            if(text == null || text.trim().isEmpty()){
                log.info("OM-SO searchInSalesOrders(): No search text provided, returning all orders");
                return getAllSummarySalesOrders(sortBy, sortDirection, page, size);
            }
            Page<SalesOrder> salesOrderPage = omSoSearchStrategy.searchInSo(text, page, size, sortBy, sortDirection);
            return salesOrderServiceHelper.transformToSummarySalesOrderView(salesOrderPage);
        } catch (Exception e) {
            log.error("OM-SO searchInSalesOrders(): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred: ", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> filterSalesOrders(SalesOrderStatus statusValue, String optionDateValue,
                                                                      LocalDate startDate, LocalDate endDate,
                                                                      int page, int size, String sortBy, String sortDirection){
        try{
            // Validate and prepare the pageable
            Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDirection);

            // Filter the orders
            Page<SalesOrder> salesOrderPage =
                    filterSalesOrdersInOm.filterSalesOrders(statusValue, optionDateValue, startDate, endDate, pageable);
            return salesOrderServiceHelper.transformToSummarySalesOrderView(salesOrderPage);
        } catch (IllegalArgumentException e) {
            log.error("OM-SO filterSalesOrders(): Invalid filter parameters: {}", e.getMessage(), e);
            throw new ValidationException("Invalid filter parameters: " + e.getMessage());
        } catch (Exception e) {
            log.error("OM-SO filterSalesOrders(): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred: ", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Long countInProgressSalesOrders() {
        try {
            return salesOrderRepository.countInProgressSalesOrders();
        } catch (DataAccessException e) {
            log.error("OM-SO (getTotalSalesOrdersCount): Database error - {}", e.getMessage(), e);
            throw new DatabaseException("Failed to count sales orders", e);
        }
    }

    // Note that: the Order Reference max prefix number is "SO-yyyy-MM-dd-999"
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateOrderReference(LocalDate now) {
        try {
            // Get the latest order reference for today with pessimistic lock
            Optional<SalesOrder> lastOrderOpt = salesOrderRepository.findLatestSalesOrderWithPessimisticLock(
                    "SO-" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "%");

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String todayPrefix = "SO-" + now.format(dateFormatter) + "-";

            if (lastOrderOpt.isPresent()) {
                SalesOrder lastSalesOrder = lastOrderOpt.get();
                String lastOrderReference = lastSalesOrder.getOrderReference();

                // Validate that the reference belongs to today
                if (lastOrderReference.startsWith(todayPrefix)) {
                    String[] splitReference = lastOrderReference.split("-");

                    if (splitReference.length >= 5) {
                        try {
                            int lastOrderNumber = Integer.parseInt(splitReference[4]);
                            int nextOrderNumber = lastOrderNumber + 1;
                            String paddedOrderNumber = String.format("%03d", nextOrderNumber);
                            return todayPrefix + paddedOrderNumber;

                        } catch (NumberFormatException e) {
                            log.error("OM-SO generateOrderReference(): Invalid order number format in reference: {}", lastOrderReference);
                            // Fall through to generate first order of the day
                        }
                    }
                }
            }

            // First order of the day or fallback case
            return todayPrefix + "001";

        } catch (Exception e) {
            log.error("OM-SO generateOrderReference(): Error generating order reference - {}", e.getMessage());
            throw new ServiceException("Failed to generate unique order reference", e);
        }
    }

    private SalesOrder getSalesOrderById(Long orderId) {
        return salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder with ID: " + orderId + " not found"));
    }
}
