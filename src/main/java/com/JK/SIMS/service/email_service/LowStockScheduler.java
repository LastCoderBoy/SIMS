package com.JK.SIMS.service.email_service;

import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.repository.InventoryControl_repo.IC_repository;
import com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.inventoryQueryService.InventoryQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.JK.SIMS.service.generalUtils.EntityConstants.DEFAULT_SORT_BY;
import static com.JK.SIMS.service.generalUtils.EntityConstants.DEFAULT_SORT_DIRECTION;

@Service
@Slf4j
@RequiredArgsConstructor
public class LowStockScheduler {

    private final InventoryQueryService inventoryQueryService;
    private final EmailSender emailSender;


//    @Scheduled(cron = "*/30 * * * * ?")
    @Scheduled(cron = "0 0 8 * * ?")
    public void sendDailyLowStockAlert() {
        List<InventoryControlData> lowStockProducts =
                inventoryQueryService.getAllLowStockProducts(DEFAULT_SORT_BY, DEFAULT_SORT_DIRECTION);
        if (lowStockProducts.isEmpty()) {
            return; // nothing to send
        }
        log.info("Sending daily low stock alerts product size {}.", lowStockProducts.size());
        String html = buildLowStockHtml(lowStockProducts);
        emailSender.sendLowStockEmail( "Daily Low Stock Alert", html);
    }

    public String buildLowStockHtml(List<InventoryControlData> lowStockProducts) {
        StringBuilder html = new StringBuilder();
        html.append("<h2 style='color:#d9534f;'>Low Stock Alert - SIMS Inventory</h2>");
        html.append("<p>The following products are below the minimum stock level:</p>");
        html.append("<table border='1' cellpadding='8' cellspacing='0' style='border-collapse:collapse;'>");
        html.append("<tr style='background-color:#f2f2f2;'><th>SKU</th><th>Product Name</th><th>Category</th><th>Stock</th><th>Min Level</th></tr>");

        for (InventoryControlData product : lowStockProducts) {
            html.append("<tr>")
                    .append("<td>").append(product.getSKU()).append("</td>")
                    .append("<td>").append(product.getPmProduct().getName()).append("</td>")
                    .append("<td>").append(product.getPmProduct().getCategory()).append("</td>")
                    .append("<td>").append(product.getCurrentStock()).append("</td>")
                    .append("<td>").append(product.getMinLevel()).append("</td>")
                    .append("</tr>");
        }

        html.append("</table>");
        html.append("<p style='margin-top:20px;'>Please restock as soon as possible.</p>");
        return html.toString();
    }

}
