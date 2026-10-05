package com.manh.ecom_be.controllers;


import com.manh.ecom_be.components.metrics.BusinessMetrics;
import com.manh.ecom_be.dtos.payment.PaymentDTO;
import com.manh.ecom_be.dtos.payment.PaymentQueryDTO;
import com.manh.ecom_be.dtos.payment.PaymentRefundDTO;
import com.manh.ecom_be.responses.ApiResponse;
import com.manh.ecom_be.services.vnpay.VNPayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/payments")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.payments.vnpay.enabled", havingValue = "true")
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class PaymentController {
    private final VNPayService vnPayService;
    private final BusinessMetrics businessMetrics;

    @PostMapping("/create_payment_url")
    public ResponseEntity<ApiResponse<String>> createPaymentUrl(
            @RequestBody PaymentDTO paymentRequest,
            HttpServletRequest request
    ) {
        try {
            String paymentUrl = vnPayService.createPaymentUrl(paymentRequest, request);
            businessMetrics.incrementPaymentsSuccess();
            return ResponseEntity.ok(ApiResponse.success(paymentUrl, "Payment URL generated successfully"));
        } catch (Exception e) {
            businessMetrics.incrementPaymentsFailed();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Error generating payment URL: " + e.getMessage()));
        }
    }

    @PostMapping("/query")
    public ResponseEntity<ApiResponse<String>> queryTransaction(
            @RequestBody PaymentQueryDTO paymentQueryDTO, HttpServletRequest request) {
        try {
            String result = vnPayService.queryTransaction(paymentQueryDTO, request);
            return ResponseEntity.ok(ApiResponse.success(result, "Query successful"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Error querying transaction: " + e.getMessage()));
        }
    }

    @PostMapping("/refund")
    public ResponseEntity<ApiResponse<String>> refundTransaction(
            @Valid @RequestBody PaymentRefundDTO paymentRefundDTO
    ) {
        try {
            String response = vnPayService.refundTransaction(paymentRefundDTO);
            return ResponseEntity.ok(ApiResponse.success(response, "Refund processed successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Failed to process refund: " + e.getMessage()));
        }
    }
}
