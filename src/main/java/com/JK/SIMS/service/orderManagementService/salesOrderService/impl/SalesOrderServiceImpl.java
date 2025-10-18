package com.JK.SIMS.service.orderManagementService.salesOrderService.impl;

import com.JK.SIMS.config.security.SecurityUtils;
import com.JK.SIMS.exceptionHandler.*;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.*;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderResponseDto;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.views.DetailedSalesOrderView;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.dtos.BulkOrderItemsRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.dtos.OrderItemRequestDto;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.SalesOrder_Repo.OrderItemRepository;
import com.JK.SIMS.repository.SalesOrder_Repo.SalesOrderRepository;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.StockManagementLogic;
import com.JK.SIMS.service.utilities.SalesOrderServiceHelper;
import com.JK.SIMS.service.orderManagementService.salesOrderService.SalesOrderService;
import com.JK.SIMS.service.productManagementService.PMServiceHelper;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import jakarta.annotation.Nullable;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
public class SalesOrderServiceImpl implements SalesOrderService {
    private static final Logger logger = LoggerFactory.getLogger(SalesOrderServiceImpl.class);

    private final GlobalServiceHelper globalServiceHelper;
    private final PMServiceHelper pmServiceHelper;
    private final StockManagementLogic stockManagementLogic;
    private final SalesOrderServiceHelper salesOrderServiceHelper;
    private final SecurityUtils securityUtils;

    private final SalesOrderRepository salesOrderRepository;
    private final OrderItemRepository orderItemRepository;
    @Autowired
    public SalesOrderServiceImpl(GlobalServiceHelper globalServiceHelper, SalesOrderRepository salesOrderRepository,
                                 OrderItemRepository orderItemRepository, PMServiceHelper pmServiceHelper,
                                 StockManagementLogic stockManagementLogic, SalesOrderServiceHelper salesOrderServiceHelper, SecurityUtils securityUtils) {
        this.globalServiceHelper = globalServiceHelper;
        this.salesOrderRepository = salesOrderRepository;
        this.orderItemRepository = orderItemRepository;
        this.pmServiceHelper = pmServiceHelper;
        this.stockManagementLogic = stockManagementLogic;
        this.salesOrderServiceHelper = salesOrderServiceHelper;
        this.securityUtils = securityUtils;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> getAllSummarySalesOrders(String sortBy, String sortDirection, int page, int size) {
        try {
            Sort sort = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);

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
    public ApiResponse<String> createOrder(@Valid SalesOrderRequestDto salesOrderRequestDto, String jwtToken) {
        List<OrderItem> reservedItems = new ArrayList<>();
        try {
            // Validate the request and Create the Entity
            salesOrderServiceHelper.validateSalesOrderItems(salesOrderRequestDto.getOrderItems());
            String user = securityUtils.validateAndExtractUsername(jwtToken);
            String orderReference = generateOrderReference(LocalDate.now());
            SalesOrder salesOrder = createSalesOrderEntity(salesOrderRequestDto, orderReference, user);

            // Process each item with stock reservation
            populateSalesOrderWithItems(salesOrder, salesOrderRequestDto.getOrderItems());
            reservedItems.addAll(salesOrder.getItems()); // Track reserved items

            salesOrderRepository.save(salesOrder); // This might fail
            logger.info("OM-SO createOrder(): SalesOrder created successfully with reference ID: {}", orderReference);
            return new ApiResponse<>(true, "SalesOrder created successfully and it is under APPROVED status");

        } catch (ValidationException | InsufficientStockException | ResourceNotFoundException e) {
            // Expected business exceptions - rollback reservations
            rollbackReservations(reservedItems);
            logger.error("OM-SO createOrder(): Validation error - {}", e.getMessage());
            throw e;
        } catch (DataIntegrityViolationException e) {
            // Unexpected constraint violation - rollback reservations
            rollbackReservations(reservedItems);
            if (e.getCause() instanceof ConstraintViolationException) {
                logger.warn("SalesOrder reference collision detected");
            }
            logger.error("OM-SO createOrder(): Data integrity error - {}", e.getMessage());
            throw new DatabaseException("Failed to save the order due to data integrity violation", e);
        } catch (DataAccessException dae) {
            // Database errors - rollback reservations
            rollbackReservations(reservedItems);
            logger.error("OM-SO createOrder(): Database error - {}", dae.getMessage());
            throw new DatabaseException("OM-SO createOrder(): Failed to save the order to the database.", dae);
        } catch (Exception e) {
            // Any other unexpected error - rollback reservations
            rollbackReservations(reservedItems);
            logger.error("OM-SO createOrder(): Unexpected error - {}", e.getMessage());
            throw new ServiceException("Internal Service error occurred while creating the order.", e);
        }
    }

    private void rollbackReservations(List<OrderItem> reservedItems) {
        for (OrderItem item : reservedItems) {
            try {
                stockManagementLogic.releaseReservation(
                        item.getProduct().getProductID(),
                        item.getQuantity()
                );
                logger.debug("Rolled back reservation for product: {}", item.getProduct().getProductID());
            } catch (Exception rollbackEx) {
                logger.error("Failed to rollback reservation for product {}: {}",
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

    private void updateItemQuantity(SalesOrder salesOrder, List<OrderItemRequestDto> orderItemsDto) {
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
            for(OrderItemRequestDto itemDto : orderItemsDto) {
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
        try {
            SalesOrder salesOrder = getSalesOrderById(orderId);
            if (salesOrder.isFinalized()) {
                log.warn("OM-SO addItemsToSalesOrder(): Order with ID {} already processed, cannot be updated!", orderId);
                throw new ValidationException("Order is already processed, cannot be updated!");
            }
            salesOrderServiceHelper.validateSalesOrderItems(bulkOrderItemsRequestDto.getOrderItems());
            String updatedUser = securityUtils.validateAndExtractUsername(jwtToken);

            // Process and add items with stock reservation
            populateSalesOrderWithItems(salesOrder, bulkOrderItemsRequestDto.getOrderItems());

            salesOrder.setUpdatedBy(updatedUser);
            salesOrderRepository.save(salesOrder);
            logger.info("OM-SO addItemsToSalesOrder(): New Items added to Sales Order: {}", salesOrder.getOrderReference());
            return new ApiResponse<>(true, "Order Item(s) added successfully");

        } catch (ValidationException | InsufficientStockException | ResourceNotFoundException e) {
            logger.error("OM-SO addItemsToSalesOrder(): Error - {}", e.getMessage());
            throw e;
        } catch (DataAccessException dae) {
            logger.error("OM-SO addItemsToSalesOrder(): Database error - {}", dae.getMessage());
            throw new DatabaseException("OM-SO addItemsToSalesOrder(): Failed to add items to the order.", dae);
        } catch (Exception e) {
            logger.error("OM-SO addItemsToSalesOrder(): Unexpected error - {}", e.getMessage());
            throw new ServiceException("Internal Service error occurred while adding items to the order.", e);
        }
    }

    /**
     * Populates SalesOrder with OrderItems after reserving stock.
     * This method is NOT marked as @Transactional because it's always called
     * from a method that already has an active transaction.
     *
     * @param salesOrder The sales order to populate
     * @param orderItemRequestDtoList List of order items to add
     * @throws ValidationException if product is not active
     * @throws InsufficientStockException if stock is insufficient
     * @throws ResourceNotFoundException if product/inventory not found
     */
    private void populateSalesOrderWithItems(SalesOrder salesOrder, List<OrderItemRequestDto> orderItemRequestDtoList){
        List<OrderItem> items = new ArrayList<>();
        try {
            for (OrderItemRequestDto itemDto : orderItemRequestDtoList ) {
                ProductsForPM product = pmServiceHelper.findProductById(itemDto.getProductId());

                // Validate the ordered Product Status
                if (product.getStatus() != ProductStatus.ACTIVE && product.getStatus() != ProductStatus.ON_ORDER) {
                    throw new ValidationException("Product '" + product.getName() + " ID: " + product.getProductID() + " is not active.");
                }
                // Reserve stock atomically - throws "InsufficientStockException" if insufficient stock
                stockManagementLogic.reserveStock(product.getProductID(), itemDto.getQuantity());

                // Create and Add OrderItem to SalesOrder
                OrderItem orderItem = createOrderItem(product, itemDto.getQuantity());
                items.add(orderItem);
            }
            // Add all OrderItems to the SalesOrder after successful reservation
            for(OrderItem item : items) {
                salesOrder.addOrderItem(item);
            }
        } catch (Exception e) {
            // Rollback the reservations if any error occurred
            rollbackReservations(items);
            log.error("OM-SO populateSalesOrderWithItems(): Error - {}", e.getMessage());
            throw e;
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
            salesOrderServiceHelper.updateSalesOrderStatus(salesOrder);
            salesOrderRepository.save(salesOrder);
            logger.info("OM-SO removeItemFromSalesOrder(): Item {} removed from Sales Order {}", itemId, salesOrder.getOrderReference());
            return new ApiResponse<>(true, "Item removed successfully");
        } catch (ResourceNotFoundException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("OM-SO removeItemFromSalesOrder(): Unexpected error - {}", e.getMessage());
            throw new ServiceException("Internal error removing item", e);
        }
    }

    // Will be used in the SORT logic and the normal GET all logic.
    // Can be only sorted using Status.
    @Transactional(readOnly = true)
    public PaginatedResponse<SalesOrderResponseDto> getAllSalesOrdersSorted(int page, int size, String sortBy, String sortDir,
                                                                            Optional<SalesOrderStatus> status) {
        try {

            Sort sort = sortDir.equalsIgnoreCase("desc") ?
                    Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);

            // If status is provided, filter by status; otherwise get all orders
            Page<SalesOrder> orders = (status.isPresent()) ?
                    salesOrderRepository.findByStatus(status.get(), pageable) :
                    salesOrderRepository.findAll(pageable);

            Page<SalesOrderResponseDto> dtoResponse = orders.map(salesOrderServiceHelper::convertToSalesOrderResponseDto);
            return new PaginatedResponse<>(dtoResponse);

        } catch (Exception e) {
            logger.error("OS (getAllSalesOrdersSorted): Error fetching orders - {}", e.getMessage());
            throw new ServiceException("Failed to fetch orders", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SalesOrderResponseDto> searchOutgoingStock(String text, int page, int size, Optional<SalesOrderStatus> status) {
        try {
            Optional<String> inputText = Optional.ofNullable(text);
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by("orderDate").ascending());
                Page<SalesOrder> outgoingStockData = salesOrderRepository.searchOutgoingStock(
                        inputText.get().trim().toLowerCase(),
                        status.orElse(null),
                        pageable
                );

                logger.info("OS (searchOutgoingStock): {} orders retrieved.", outgoingStockData.getContent().size());
                Page<SalesOrderResponseDto> dtoResponse = outgoingStockData.map(salesOrderServiceHelper::convertToSalesOrderResponseDto);
                return new PaginatedResponse<>(dtoResponse);
            }
            logger.info("OS (searchOutgoingStock): No search text provided. Retrieving first page with default size.");
            return getAllSalesOrdersSorted(page, size, "orderDate", "asc", Optional.of(SalesOrderStatus.PENDING));
        } catch (Exception e) {
            logger.error("OS (searchOutgoingStock): Failed to retrieve outgoing stock data - {}", e.getMessage());
            throw new ServiceException("Failed to retrieve outgoing stock data", e);
        }
    }

    private SalesOrder createSalesOrderEntity(SalesOrderRequestDto salesOrderRequestDto, String orderReference, String createdPerson ) {
        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setOrderReference(orderReference);
        salesOrder.setDestination(salesOrderRequestDto.getDestination().trim());
        salesOrder.setStatus(SalesOrderStatus.PENDING);
        salesOrder.setCreatedBy(createdPerson);
        salesOrder.setCustomerName(salesOrderRequestDto.getCustomerName().trim());

        // Calculate estimated delivery date (7 business days)
        LocalDateTime estimatedDelivery = LocalDateTime.now().plusDays(7);
        salesOrder.setEstimatedDeliveryDate(estimatedDelivery);

        // To avoid null pointer exception
        salesOrder.setItems(new ArrayList<>());

        log.debug("OM-SO createSalesOrderEntity(): Created salesOrder entity with reference {}", orderReference);
        return salesOrder;
    }

    private OrderItem createOrderItem(ProductsForPM product, Integer orderedQuantity) {
        if (product == null || orderedQuantity == null || orderedQuantity <= 0) {
            throw new ValidationException("Invalid product or quantity for order item");
        }
        BigDecimal unitPriceAtOrder = product.getPrice();
        BigDecimal totalPrice = unitPriceAtOrder.multiply(BigDecimal.valueOf(orderedQuantity));
        return new OrderItem(orderedQuantity, product, totalPrice);
    }


    // TODO: If goes over XXX number, go to XXXX numbers
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateOrderReference(LocalDate now) {
        try {
            // Get the latest order reference for today with pessimistic lock
            Optional<SalesOrder> lastOrderOpt = salesOrderRepository.findLatestOrderByReferencePattern(
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

    @Transactional(readOnly = true)
    public SalesOrder getSalesOrderById(Long orderId) {
        return salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder with ID: " + orderId + " not found"));
    }
}
