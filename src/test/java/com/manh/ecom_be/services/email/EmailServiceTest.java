package com.manh.ecom_be.services.email;

import java.math.BigDecimal;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailServiceTest {
    @Test void orderConfirmationRendersAndSends() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        EmailService service = new EmailService(sender);
        ReflectionTestUtils.setField(service, "from", "shop@example.test");
        service.sendOrderConfirmationEmail("buyer@example.test", 42L, new BigDecimal("200"));
        verify(sender).send(message);
        assertThat(message.getSubject()).contains("#42");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("buyer@example.test");
    }

    @Test void smtpFailureDoesNotFailOrderRequest() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenThrow(new IllegalStateException("SMTP offline"));
        assertThatCode(() -> new EmailService(sender).sendOrderConfirmationEmail("buyer@example.test", 42L, new BigDecimal("200")))
                .doesNotThrowAnyException();
    }
}
