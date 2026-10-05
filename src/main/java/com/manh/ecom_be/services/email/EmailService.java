package com.manh.ecom_be.services.email;

import java.math.BigDecimal;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Asynchronous email notification service.
 *
 * Uses @Async so the calling thread (API handler) returns immediately without
 * waiting for SMTP delivery. Emails are processed on a dedicated thread pool
 * defined in AsyncConfig to avoid consuming the main request thread pool.
 *
 * Only active when spring.mail.enabled=true (avoids startup failure when
 * SMTP credentials are not configured, e.g., in test/local environments).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "spring.mail.enabled", havingValue = "true", matchIfMissing = false)
public class EmailService {

    private final JavaMailSender mailSender;

    @org.springframework.beans.factory.annotation.Value("${spring.mail.from:${spring.mail.username:}}")
    private String from;

    @Async("emailExecutor")
    @org.springframework.transaction.event.TransactionalEventListener
    public void onOrderPlaced(OrderPlaced event) {
        if (event.email() != null && !event.email().isBlank()) {
            sendOrderConfirmationEmail(event.email(), event.orderId(), event.totalMoney());
        }
    }

    /**
     * Send an order confirmation email asynchronously.
     * The method returns void so Spring can fire-and-forget without blocking.
     *
     * @param toEmail    recipient email address
     * @param orderId    the order ID to display in the email
     * @param totalMoney order total amount (VND)
     */
    @Async("emailExecutor")
    public void sendOrderConfirmationEmail(String toEmail, Long orderId, BigDecimal totalMoney) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setFrom(from);
            helper.setSubject("Xác nhận đơn hàng #" + orderId);
            helper.setText(buildOrderConfirmationHtml(orderId, totalMoney), true);

            mailSender.send(message);
            log.info("Order confirmation email sent to {} for order #{}", toEmail, orderId);
        } catch (Exception e) {
            // Log but never throw — a failed email must not rollback the order transaction
            log.error("Failed to send order confirmation email to {} for order #{}: {}",
                    toEmail, orderId, e.getMessage());
        }
    }

    /**
     * Send a welcome/registration confirmation email asynchronously.
     *
     * @param toEmail  new user's email
     * @param fullName new user's display name
     */
    @Async("emailExecutor")
    public void sendWelcomeEmail(String toEmail, String fullName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("Chào mừng bạn đến với EcomShop!");
            helper.setText(buildWelcomeHtml(fullName), true);

            mailSender.send(message);
            log.info("Welcome email sent to {} ({})", toEmail, fullName);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ─────────────────────── HTML templates ───────────────────────

    private String buildOrderConfirmationHtml(Long orderId, BigDecimal totalMoney) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head><meta charset="UTF-8"/></head>
                <body style="font-family:Arial,sans-serif;background:#f4f4f4;padding:20px">
                  <div style="max-width:600px;margin:0 auto;background:#fff;border-radius:8px;padding:30px">
                    <h2 style="color:#2c7be5">✅ Đặt hàng thành công!</h2>
                    <p>Cảm ơn bạn đã mua hàng tại <strong>EcomShop</strong>.</p>
                    <table style="width:100%%;border-collapse:collapse;margin-top:16px">
                      <tr><td style="padding:8px;color:#666">Mã đơn hàng:</td>
                          <td style="padding:8px;font-weight:bold">#%d</td></tr>
                      <tr style="background:#f9f9f9">
                          <td style="padding:8px;color:#666">Tổng tiền:</td>
                          <td style="padding:8px;font-weight:bold;color:#e74c3c">%,.2f VNĐ</td></tr>
                    </table>
                    <p style="margin-top:20px;color:#888;font-size:12px">
                      Nếu bạn có thắc mắc, vui lòng liên hệ support@ecomshop.vn
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(orderId, totalMoney);
    }

    private String buildWelcomeHtml(String fullName) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head><meta charset="UTF-8"/></head>
                <body style="font-family:Arial,sans-serif;background:#f4f4f4;padding:20px">
                  <div style="max-width:600px;margin:0 auto;background:#fff;border-radius:8px;padding:30px">
                    <h2 style="color:#2c7be5">🎉 Chào mừng, %s!</h2>
                    <p>Tài khoản của bạn đã được tạo thành công trên <strong>EcomShop</strong>.</p>
                    <p>Khám phá hàng nghìn sản phẩm với giá tốt nhất ngay hôm nay!</p>
                    <a href="http://localhost:4200" style="display:inline-block;margin-top:16px;padding:12px 24px;
                       background:#2c7be5;color:#fff;text-decoration:none;border-radius:4px">
                      Mua sắm ngay →
                    </a>
                  </div>
                </body>
                </html>
                """.formatted(org.springframework.web.util.HtmlUtils.htmlEscape(fullName == null ? "" : fullName));
    }
}
