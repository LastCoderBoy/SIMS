package com.JK.SIMS.service.InventoryServices.inventoryPageService;

import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.models.inventoryData.dtos.InventoryPageResponse;
import com.JK.SIMS.models.inventoryData.dtos.PendingOrdersResponseInIC;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface InventoryControlService {
    InventoryPageResponse getInventoryControlPageData(int page, int size);

    PaginatedResponse<PendingOrdersResponseInIC> getAllPendingOrders(int page, int size);

    void addProduct(ProductsForPM product, boolean isUnderTransfer);

    void saveInventoryProduct(InventoryControlData inventoryControlData);

    void deleteByProductId(String productId);

    void updateInventoryStatus(Optional<InventoryControlData> productInIcOpt, InventoryDataStatus status);

    PaginatedResponse<PendingOrdersResponseInIC> searchByTextPendingOrders(String text, int page, int size);

    PaginatedResponse<PendingOrdersResponseInIC> filterPendingOrders(String type, SalesOrderStatus soStatus,
                                                                     PurchaseOrderStatus poStatus, String dateOption,
                                                                     LocalDate startDate, LocalDate endDate,
                                                                     ProductCategories category, String sortBy,
                                                                     String sortDirection, int page, int size);
}
