package com.JK.SIMS.service.email_service;

import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LowStockScheduler {
    private final IC_repository icRepository;
    private final EmailService emailService;

    @Autowired
    public LowStockScheduler(IC_repository icRepository, EmailService emailService) {
        this.icRepository = icRepository;
        this.emailService = emailService;
    }

    //@Scheduled(cron = "*/30 * * * * ?")
    @Scheduled(cron = "0 0 8 * * ?")
    public void sendDailyLowStockAlert() {
        List<InventoryData> lowStockProducts = icRepository.getLowStockItems();
        if (lowStockProducts.isEmpty()) {
            return; // nothing to send
        }

        String html = buildLowStockHtml(lowStockProducts);
        emailService.sendLowStockEmail( "Daily Low Stock Alert", html);
    }

    public String buildLowStockHtml(List<InventoryData> lowStockProducts) {
        StringBuilder html = new StringBuilder();
        html.append("<h2 style='color:#d9534f;'>Low Stock Alert - SIMS Inventory</h2>");
        html.append("<p>The following products are below the minimum stock level:</p>");
        html.append("<table border='1' cellpadding='8' cellspacing='0' style='border-collapse:collapse;'>");
        html.append("<tr style='background-color:#f2f2f2;'><th>SKU</th><th>Product Name</th><th>Category</th><th>Stock</th><th>Min Level</th></tr>");

        for (InventoryData product : lowStockProducts) {
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
