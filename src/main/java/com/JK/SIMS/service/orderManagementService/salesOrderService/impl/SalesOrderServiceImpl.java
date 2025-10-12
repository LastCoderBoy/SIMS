package com.JK.SIMS.service.orderManagementService.salesOrderService.impl;

import com.JK.SIMS.config.security.SecurityUtils;
import com.JK.SIMS.exceptionHandler.*;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.*;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderResponseDto;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.views.DetailedSalesOrderView;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItemRequestDto;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

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
    @Transactional
    public ApiResponse<String> createOrder(SalesOrderRequestDto salesOrderRequestDto, String jwtToken) {
        try {
            // Validate the request and Create the Entity
            salesOrderServiceHelper.validateSoRequestForCreate(salesOrderRequestDto); // might throw ValidationException
            String user = securityUtils.validateAndExtractUsername(jwtToken);
            String orderReference = generateOrderReference(LocalDate.now());
            SalesOrder salesOrder = createSalesOrderEntity(salesOrderRequestDto, orderReference, user);

            // Process each item with stock reservation
            List<OrderItem> items = new ArrayList<>();
            try {
                for (OrderItemRequestDto itemDto : salesOrderRequestDto.getOrderItems()) {
                    ProductsForPM product = pmServiceHelper.findProductById(itemDto.getProductId());

                    // Validate the ordered Product Status
                    if (product.getStatus() != ProductStatus.ACTIVE && product.getStatus() != ProductStatus.ON_ORDER) {
                        throw new ValidationException("Product '" + product.getName() + "ID: " + product.getProductID() + " is not active.");
                    }
                    // Reserve stock atomically - throws "InsufficientStockException" if insufficient stock
                    stockManagementLogic.reserveStock(product.getProductID(), itemDto.getQuantity());

                    // Create and Add OrderItem to SalesOrder
                    OrderItem orderItem = createOrderItem(product, itemDto.getQuantity());
                    items.add(orderItem);
                }
                // Add all OrderItems to the SalesOrder after successful reservation
                log.debug("OM-SO createOrder(): Adding {} OrderItems to the SalesOrder", items); // debug
                for(OrderItem item : items) {
                    salesOrder.addOrderItem(item);
                }
                log.info("OM-SO createOrder(): OrderItems added to the SalesOrder successfully List: {}", items.toString()); // debug
            } catch (Exception e) {
                // Rollback the reservations if any error occurred
                for (OrderItem item : items) {
                    stockManagementLogic.releaseReservation(item.getProduct().getProductID(), item.getQuantity());
                }
                throw e;
            }
            salesOrderRepository.save(salesOrder);
            logger.info("OM-SO createOrder(): SalesOrder created successfully with reference ID: {}", orderReference);
            return new ApiResponse<>(true, "SalesOrder created successfully and it is under APPROVED status");

        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                logger.warn("SalesOrder reference collision detected, retrying...");
            }
            throw e;
        } catch (ValidationException | InsufficientStockException | ResourceNotFoundException e) {
            logger.error("OM-SO createOrder(): Error - {}", e.getMessage());
            throw e;
        } catch (DataAccessException dae) {
            logger.error("OM-SO createOrder(): Database error - {}", dae.getMessage());
            throw new DatabaseException("OM-SO createOrder(): Failed to save the order to the database.", dae);
        } catch (Exception e) {
            logger.error("OM-SO createOrder(): Unexpected error - {}", e.getMessage());
            throw new ServiceException("Internal Service error occurred while creating the order.", e);
        }
    }

    @Override
    @Transactional
    public ApiResponse<String> updateSalesOrder(Long orderId, SalesOrderRequestDto salesOrderRequestDto, String jwtToken) {
        salesOrderServiceHelper.validateSoRequestForUpdate(salesOrderRequestDto);
        SalesOrder orderToBeUpdated = getSalesOrderById(orderId);
        if(orderToBeUpdated.isFinalized()){
            log.warn("OM-SO updateSalesOrder(): Order with ID {} already processed, cannot be updated!", orderId);
            throw new ValidationException("Order is already processed, cannot be updated!");
        }
        updateBaseFieldsIfProvided(orderToBeUpdated, salesOrderRequestDto.getDestination(), salesOrderRequestDto.getCustomerName());
        updateOrderItemsIfProvided(orderToBeUpdated, salesOrderRequestDto.getOrderItems());
        return null;
    }

    private void updateBaseFieldsIfProvided(SalesOrder orderToBeUpdated, @Nullable String destination, @Nullable String customerName) {
        Optional.ofNullable(destination).ifPresent(dest -> orderToBeUpdated.setDestination(dest.trim()));
        Optional.ofNullable(customerName).ifPresent(name -> orderToBeUpdated.setCustomerName(name.trim()));
    }

    // Why marked as Valid, bcz we have to know which product we are going to update.
    private void updateOrderItemsIfProvided(SalesOrder orderToBeUpdated, @Valid List<OrderItemRequestDto> orderItemsDto) {
        if (orderItemsDto == null || orderItemsDto.isEmpty()) {
            return; // Nothing to update
        }
        List<OrderItem> existingItems = orderToBeUpdated.getItems();
        HashMap<String, OrderItem> orderItemMap = new HashMap<>();
        for (OrderItem item : existingItems) {
            orderItemMap.put(item.getProduct().getProductID(), item);
        }
        List<OrderItem> itemsToAdd = new ArrayList<>();
        try {
            for(OrderItemRequestDto itemDto : orderItemsDto) {
                String productId = itemDto.getProductId();
                if(orderItemMap.containsKey(productId)){
                    OrderItem existingItem = orderItemMap.get(productId);
                    int quantityDifference = itemDto.getQuantity() - existingItem.getQuantity();
                    if(quantityDifference > 0){
                        // Need to reserve more stock
                        stockManagementLogic.reserveStock(productId, quantityDifference);
                    } else if(quantityDifference < 0){
                        // Need to release the excess stock
                        stockManagementLogic.releaseReservation(productId, Math.abs(quantityDifference));
                    }
                    existingItem.setQuantity(itemDto.getQuantity());
                    existingItem.setOrderPrice(existingItem.getProduct().getPrice().multiply(BigDecimal.valueOf(itemDto.getQuantity())));
                } else {
                    // Need to add a new item
                    ProductsForPM product = pmServiceHelper.findProductById(productId);

                    if (product.getStatus() != ProductStatus.ACTIVE) {
                        throw new ValidationException("Product '" + product.getName() + "' (ID: " + productId + ") is not active.");
                    }
                    stockManagementLogic.reserveStock(productId, itemDto.getQuantity());
                    OrderItem orderItem = createOrderItem(product, itemDto.getQuantity());
                    itemsToAdd.add(orderItem);
                }
            }
            existingItems.addAll(itemsToAdd);
            salesOrderRepository.save(orderToBeUpdated);

            log.info("OM-SO updateOrderItemsIfProvided(): Successfully updated order items for order {}",
                    orderToBeUpdated.getOrderReference());

        } catch (Exception e) {
            // Rollback all new reservations
            for (OrderItem item : itemsToAdd) {
                stockManagementLogic.releaseReservation(
                        item.getProduct().getProductID(),
                        item.getQuantity()
                );
            }
            log.error("OM-SO updateOrderItemsIfProvided(): Failed to update order items - {}", e.getMessage());
            throw e;
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
    public ApiResponse<String> cancelSalesOrder(Long orderId, String jwtToken) {
        return null;
    }

    @Transactional(readOnly = true)
    public SalesOrder getSalesOrderById(Long orderId) {
        return salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder with ID: " + orderId + " not found"));
    }
}
