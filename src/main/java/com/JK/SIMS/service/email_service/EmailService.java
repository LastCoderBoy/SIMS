package com.JK.SIMS.service.email_service;

import com.JK.SIMS.models.IC_models.incoming.IncomingStock;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${alert.receive.email}")
    private String lowStockReceiver;

    @Value("${spring.mail.username}")
    private String sender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendLowStockEmail(String subject, String htmlBody){
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(sender, "SIMS Inventory System");
            helper.setTo(lowStockReceiver);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // `true` means it's HTML

            mailSender.send(message);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    @Async
    public void sendOrderRequest(String supplierEmail, IncomingStock order) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8"); // true for multipart (HTML content)

            helper.setFrom(sender, "SIMS Inventory System");
            helper.setTo(supplierEmail);
            helper.setSubject("Purchase Order Request: " + order.getPONumber() + " - " + order.getProduct().getName());
            helper.setText(buildOrderRequestHtml(order), true); // `true` indicates HTML content

            mailSender.send(message);
            logger.info("Purchase order request email sent successfully to {} for PO Number: {}", supplierEmail, order.getPONumber());
        } catch (MessagingException | UnsupportedEncodingException e) {
            logger.error("Failed to send purchase order request email to {}: {}", supplierEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send purchase order request email: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the HTML content for the purchase order request email.
     * @param order The IncomingStock entity containing order details.
     * @return HTML string for the email body.
     */
    private String buildOrderRequestHtml(IncomingStock order) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String productName = order.getProduct().getName();
        String productCategory = order.getProduct().getCategory().toString();
        String poNumber = order.getPONumber();
        Integer orderedQuantity = order.getOrderedQuantity();
        LocalDate orderDate = order.getOrderDate();
        LocalDate expectedArrivalDate = order.getExpectedArrivalDate();
        String notes = order.getNotes() != null && !order.getNotes().isEmpty() ? order.getNotes() : "N/A";
        String supplierName = order.getSupplier() != null ? order.getSupplier().getName() : "Unknown Supplier";

        return "<html>"
                + "<head>"
                + "<style>"
                + "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }"
                + ".container { max-width: 600px; margin: 20px auto; padding: 20px; border: 1px solid #ddd; border-radius: 8px; background-color: #f9f9f9; }"
                + ".header { background-color: #0056b3; color: #ffffff; padding: 15px; text-align: center; border-radius: 8px 8px 0 0; }"
                + ".content { padding: 20px 0; }"
                + ".detail-table { width: 100%; border-collapse: collapse; margin-top: 15px; }"
                + ".detail-table th, .detail-table td { border: 1px solid #eee; padding: 10px; text-align: left; }"
                + ".detail-table th { background-color: #e9e9e9; }"
                + ".footer { margin-top: 30px; font-size: 0.9em; color: #777; text-align: center; }"
                + ".button { display: inline-block; padding: 10px 20px; margin-top: 20px; background-color: #28a745; color: #ffffff; text-decoration: none; border-radius: 5px; }"
                + "</style>"
                + "</head>"
                + "<body>"
                + "<div class='container'>"
                + "<div class='header'>"
                + "<h2>Purchase Order Request from SIMS Inventory System</h2>"
                + "</div>"
                + "<div class='content'>"
                + "<p>Dear " + supplierName + " Team,</p>"
                + "<p>This email serves as a formal purchase order request for the following item:</p>"
                + "<table class='detail-table'>"
                + "<tr><th>PO Number:</th><td>" + poNumber + "</td></tr>"
                + "<tr><th>Order Date:</th><td>" + orderDate.format(dateFormatter) + "</td></tr>"
                + "<tr><th>Expected Arrival:</th><td>" + expectedArrivalDate.format(dateFormatter) + "</td></tr>"
                + "<tr><th>Product Name:</th><td>" + productName + "</td></tr>"
                + "<tr><th>Product Category:</th><td>" + productCategory + "</td></tr>"
                + "<tr><th>Ordered Quantity:</th><td>" + orderedQuantity + "</td></tr>"
                + "<tr><th>Notes:</th><td>" + notes + "</td></tr>"
                + "</table>"
                + "<p>Please process this order at your earliest convenience. Kindly confirm receipt of this order and provide any necessary updates regarding its fulfillment.</p>"
                + "<p>If you have any questions or require further information, please do not hesitate to contact us.</p>"
                + "</div>"
                + "<div class='footer'>"
                + "<p>Thank you for your prompt attention to this matter.</p>"
                + "<p>Sincerely,<br>SIMS Inventory System Team</p>"
                + "<p>Contact: " + sender + "</p>"
                + "</div>"
                + "</div>"
                + "</body>"
                + "</html>";
    }
}
