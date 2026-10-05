package com.manh.ecom_be.services.vnpay;
import com.fasterxml.jackson.databind.*;
import com.manh.ecom_be.components.*;
import com.manh.ecom_be.dtos.payment.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VNPayServiceTest {
    VNPayService service;
    HttpServer server;
    final AtomicReference<JsonNode> received = new AtomicReference<>();
    final AtomicReference<String> method = new AtomicReference<>();
    final AtomicReference<String> contentType = new AtomicReference<>();
    int responseStatus = 200;
    @BeforeEach void setup() throws Exception {
        var config = mock(VNPayConfig.class);
        when(config.getSecretKey()).thenReturn("local-test-key");
        when(config.getVnpTmnCode()).thenReturn("TESTSHOP");
        when(config.getVnpPayUrl()).thenReturn("https://example.test/pay");
        when(config.getVnpReturnUrl()).thenReturn("https://example.test/return");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/payment", exchange -> {
            received.set(new ObjectMapper().readTree(exchange.getRequestBody()));
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] response = "{\"result\":\"received\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus,response.length);
            try(var stream = exchange.getResponseBody()) { stream.write(response); }
        });
        server.start();
        when(config.getVnpApiUrl()).thenReturn("http://127.0.0.1:"+server.getAddress().getPort()+"/payment");
        service = new VNPayService(config,new VNPayUtils(config));
    }
    @AfterEach void close() { server.stop(0); }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void paymentUrlHasValidSignatureAmountAndExpiry(boolean customOptions) throws Exception {
        var dto = new PaymentDTO(12345,customOptions ? "NCB" : null,customOptions ? "en" : null);
        String url = service.createPaymentUrl(dto,new MockHttpServletRequest());
        assertThat(url).startsWith("https://example.test/pay?");
        String query = URI.create(url).getRawQuery();
        String marker="&vnp_SecureHash=";
        int separator = query.lastIndexOf(marker);
        assertThat(query.substring(separator+marker.length())).isEqualTo(hmac(query.substring(0,separator)));
        Map<String,String> params = new HashMap<>();
        for(String pair:query.split("&")) {
            String[] parts=pair.split("=",2);
            params.put(parts[0],URLDecoder.decode(parts[1],StandardCharsets.US_ASCII));
        }
        assertThat(params).containsEntry("vnp_Amount","1234500").containsEntry("vnp_TmnCode","TESTSHOP")
                .containsEntry("vnp_ReturnUrl","https://example.test/return")
                .containsEntry("vnp_Locale",customOptions ? "en" : "vn");
        if(customOptions) assertThat(params).containsEntry("vnp_BankCode","NCB");
        else assertThat(params).doesNotContainKey("vnp_BankCode");
        var format = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        assertThat(Duration.between(LocalDateTime.parse(params.get("vnp_CreateDate"),format),
                LocalDateTime.parse(params.get("vnp_ExpireDate"),format))).isEqualTo(Duration.ofMinutes(15));
    }
    @Test void queryPostsTransactionIdentityAndReturnsResponse() throws Exception {
        String result=service.queryTransaction(new PaymentQueryDTO("order-42","20260918080000",null),new MockHttpServletRequest());
        assertThat(new ObjectMapper().readTree(result).get("result").asText()).isEqualTo("received");
        assertThat(method.get()).isEqualTo("POST"); assertThat(contentType.get()).contains("application/json");
        JsonNode body=received.get();
        assertThat(body.get("vnp_Command").asText()).isEqualTo("querydr");
        assertThat(body.get("vnp_TxnRef").asText()).isEqualTo("order-42");
        assertThat(body.get("vnp_TransactionDate").asText()).isEqualTo("20260918080000");
        assertThat(body.get("vnp_SecureHash").asText()).matches("[0-9a-f]{128}");
    }
    PaymentRefundDTO refund() {
        return PaymentRefundDTO.builder().orderId("order-42").amount(12345).transactionType("02")
                .transactionDate("20260918080000").createdBy("operator").ipAddress("127.0.0.1").build();
    }
    @Test void refundPostsConvertedAmountAndIdentity() throws Exception {
        assertThat(service.refundTransaction(refund())).isEqualTo("{\"result\":\"received\"}");
        JsonNode body=received.get();
        assertThat(method.get()).isEqualTo("POST");
        assertThat(body.get("vnp_Command").asText()).isEqualTo("refund");
        assertThat(body.get("vnp_TxnRef").asText()).isEqualTo("order-42");
        assertThat(body.get("vnp_Amount").asText()).isEqualTo("1234500");
        assertThat(body.get("vnp_TransactionType").asText()).isEqualTo("02");
        assertThat(body.get("vnp_CreateBy").asText()).isEqualTo("operator");
        assertThat(body.get("vnp_SecureHash").asText()).matches("[0-9a-f]{128}");
    }
    @Test void providerFailureIsNotReportedAsSuccessfulQuery() {
        responseStatus=503;
        assertThatThrownBy(() -> service.queryTransaction(new PaymentQueryDTO("42","20260918080000",null),new MockHttpServletRequest()))
                .isInstanceOf(java.io.IOException.class);
    }
    @Test void providerFailureIsNotReportedAsSuccessfulRefund() {
        responseStatus=503;
        assertThatThrownBy(() -> service.refundTransaction(refund())).isInstanceOf(RuntimeException.class).hasMessageContaining("503");
    }
    String hmac(String data) throws Exception {
        var mac=Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec("local-test-key".getBytes(StandardCharsets.UTF_8),"HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}