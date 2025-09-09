package com.JK.SIMS.service.salesOrderService;

import com.JK.SIMS.exceptionHandler.*;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.*;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItemDto;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItemResponseDto;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.outgoingStockRepo.OrderItemRepository;
import com.JK.SIMS.repository.outgoingStockRepo.SalesOrderRepository;
import com.JK.SIMS.service.InventoryControl_service.InventoryControlService;
import com.JK.SIMS.service.InventoryControl_service.InventoryServiceHelper;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import com.JK.SIMS.service.productManagement_service.ProductManagementService;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SalesOrderService {
    private static final Logger logger = LoggerFactory.getLogger(SalesOrderService.class);

    private final GlobalServiceHelper globalServiceHelper;
    private final SalesOrderRepository salesOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductManagementService pmService;
    private final InventoryControlService icService;
    private final SalesOrderServiceHelper salesOrderServiceHelper;
    @Autowired
    public SalesOrderService(GlobalServiceHelper globalServiceHelper, SalesOrderRepository salesOrderRepository, OrderItemRepository orderItemRepository, ProductManagementService pmService, @Lazy InventoryControlService icService, SalesOrderServiceHelper salesOrderServiceHelper) {
        this.globalServiceHelper = globalServiceHelper;
        this.salesOrderRepository = salesOrderRepository;
        this.orderItemRepository = orderItemRepository;
        this.pmService = pmService;
        this.icService = icService;
        this.salesOrderServiceHelper = salesOrderServiceHelper;
    }

    // TODO: Impl. the DeliveryDate and EstimateDeliveryData logic
    @Transactional
    public ApiResponse createOrder(SalesOrderRequestDto salesOrderRequestDto) {
        try {
            validateOrderRequest(salesOrderRequestDto);

            String orderReference = generateOrderReference(LocalDate.now());
            SalesOrder salesOrder = createOrderEntity(salesOrderRequestDto, orderReference);

            // Process each item with stock reservation
            for (OrderItemDto itemDto : salesOrderRequestDto.getItems()) {
                ProductsForPM product = pmService.findProductById(itemDto.getProductId());

                // Validate Product Status
                if (product.getStatus() != ProductStatus.ACTIVE && product.getStatus() != ProductStatus.ON_ORDER) {
                    throw new ValidationException("Product '" + product.getName() + "' (ID: " + product.getProductID() + ") is not active and cannot be ordered.");
                }

                // Reserve stock atomically
                boolean stockReserved = icService.reserveStock(product.getProductID(), itemDto.getQuantity());
                if (!stockReserved) {
                    throw new InsufficientStockException("Not enough stock for product '" + product.getName() + "' (ID: " + product.getProductID() + "). Requested: " + itemDto.getQuantity());
                }

                // Create and Add OrderItem to SalesOrder
                OrderItem orderItem = createOrderItem(product, itemDto.getQuantity());
                salesOrder.addOrderItem(orderItem);
            }

            salesOrderRepository.save(salesOrder);
            logger.info("OS (createOrder): SalesOrder created successfully with reference ID: {}", orderReference);
            return new ApiResponse(true, "SalesOrder created successfully and it is under PROCESSING status");

        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                logger.warn("SalesOrder reference collision detected, retrying...");
            }
            throw e;
        } catch (ValidationException | InsufficientStockException | ResourceNotFoundException e) {
            logger.error("OS (createOrder): Error - {}", e.getMessage());
            throw e;
        } catch (DataAccessException dae) {
            logger.error("OS (createOrder): Database error - {}", dae.getMessage());
            throw new DatabaseException("OS (createOrder): Failed to save the order to the database.", dae);
        } catch (Exception e) {
            logger.error("OS (createOrder): Unexpected error - {}", e.getMessage());
            throw new ServiceException("OS (createOrder): An unexpected error occurred while creating the order.", e);
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

            Page<SalesOrderResponseDto> dtoResponse = orders.map(salesOrderServiceHelper::convertToOrderResponseDto);
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
                Page<SalesOrderResponseDto> dtoResponse = outgoingStockData.map(salesOrderServiceHelper::convertToOrderResponseDto);
                return new PaginatedResponse<>(dtoResponse);
            }
            logger.info("OS (searchOutgoingStock): No search text provided. Retrieving first page with default size.");
            return getAllSalesOrdersSorted(page, size, "orderDate", "asc", Optional.of(SalesOrderStatus.PENDING));
        } catch (Exception e) {
            logger.error("OS (searchOutgoingStock): Failed to retrieve outgoing stock data - {}", e.getMessage());
            throw new ServiceException("Failed to retrieve outgoing stock data", e);
        }
    }

    private void validateOrderRequest(SalesOrderRequestDto salesOrderRequestDto) {
        if (salesOrderRequestDto == null) {
            throw new ValidationException("OS (validateOrderRequest): SalesOrder request cannot be null.");
        }

        if (salesOrderRequestDto.getItems() == null || salesOrderRequestDto.getItems().isEmpty()) {
            throw new ValidationException("OS (validateOrderRequest): SalesOrder items list cannot be empty.");
        }

        if (salesOrderRequestDto.getDestination() == null || salesOrderRequestDto.getDestination().trim().isEmpty()) {
            throw new ValidationException("OS (validateOrderRequest): Destination cannot be empty.");
        }

        if (salesOrderRequestDto.getCustomerName() == null || salesOrderRequestDto.getCustomerName().trim().isEmpty()) {
            throw new ValidationException("OS (validateOrderRequest): Customer name cannot be empty.");
        }

        // Check for duplicate products in the same order
        Set<String> productIds = new HashSet<>();
        for (OrderItemDto item : salesOrderRequestDto.getItems()) {
            if (!productIds.add(item.getProductId())) {
                throw new ValidationException("OS (validateOrderRequest): Duplicate product found in order: " + item.getProductId());
            }
        }

        logger.debug("OS (validateOrderRequest): SalesOrder request validation passed");
    }

    private SalesOrder createOrderEntity(SalesOrderRequestDto salesOrderRequestDto, String orderReference) {
        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setOrderReference(orderReference);
        salesOrder.setDestination(salesOrderRequestDto.getDestination().trim());
        salesOrder.setStatus(SalesOrderStatus.PENDING);
        salesOrder.setCustomerName(salesOrderRequestDto.getCustomerName().trim());

        logger.debug("OS (createOrderEntity): Created salesOrder entity with reference {}", orderReference);
        return salesOrder;
    }

    private OrderItem createOrderItem(ProductsForPM product, Integer orderedQuantity) {
        BigDecimal unitPriceAtOrder = product.getPrice();
        BigDecimal lineItemPrice = unitPriceAtOrder.multiply(BigDecimal.valueOf(orderedQuantity));

        return new OrderItem(orderedQuantity, product, lineItemPrice);
    }

    // internal Helper methods
    @Transactional(readOnly = true)
    public Long getTotalValidOutgoingStockSize() {
        try {
            return salesOrderRepository.getOutgoingValidStockSize();
        } catch (Exception e) {
            logger.error("OS (totalOutgoingStockSize): Error getting outgoing stock size - {}", e.getMessage());
            throw new ServiceException("Failed to get total outgoing stock size", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized String generateOrderReference(LocalDate now) {
        try {
            // Get the latest order reference for today with pessimistic lock
            Optional<SalesOrder> lastOrderOpt = salesOrderRepository.findLatestOrderByReferencePattern(
                    "SO-" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "%");

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String todayPrefix = "ORD-" + now.format(dateFormatter) + "-";

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
                            logger.error("OS (generateOrderReference): Invalid order number format in reference: {}", lastOrderReference);
                            // Fall through to generate first order of the day
                        }
                    }
                }
            }

            // First order of the day or fallback case
            return todayPrefix + "001";

        } catch (Exception e) {
            logger.error("OS (generateOrderReference): Error generating order reference - {}", e.getMessage());
            throw new ServiceException("Failed to generate unique order reference", e);
        }
    }
}
