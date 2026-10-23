package com.stayhub.payment;

import com.stayhub.booking.Booking;
import com.stayhub.booking.BookingStatus;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.ResourceNotFoundException;
import com.stayhub.discount.DiscountCodeRepository;
import com.stayhub.notification.NotificationService;
import com.stayhub.payment.dto.PaymentStatusResponse;
import com.stayhub.payment.dto.VnpayIpnResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MockPaymentService implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final VnpayPaymentService vnpayPaymentService;
    private final DiscountCodeRepository discountCodeRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public Payment createSuccessfulPayment(Booking booking) {
        Payment payment = Payment.builder()
                .booking(booking)
                .paymentMethod(PaymentMethod.MOCK)
                .status(PaymentStatus.SUCCESS)
                .amount(booking.getTotalPrice())
                .provider(PaymentMethod.MOCK.name())
                .currency("VND")
                .transactionId("MOCK-" + UUID.randomUUID())
                .paidAt(Instant.now())
                .build();
        return paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public Payment createPendingVnpayPayment(Booking booking) {
        Payment payment = Payment.builder()
                .booking(booking)
                .paymentMethod(PaymentMethod.VNPAY)
                .status(PaymentStatus.PENDING)
                .amount(booking.getTotalPrice())
                .provider(PaymentMethod.VNPAY.name())
                .currency("VND")
                .providerTxnRef("STAYHUB-" + booking.getId())
                .build();
        return paymentRepository.save(payment);
    }

    @Override
    public String buildVnpayCheckoutUrl(Payment payment, String ipAddress) {
        return vnpayPaymentService.buildPaymentUrl(new VnpayPaymentRequest(
                payment.getProviderTxnRef(),
                payment.getAmount(),
                "StayHub booking #" + payment.getBooking().getId(),
                ipAddress
        ));
    }

    @Override
    @Transactional
    public VnpayIpnResponse handleVnpayIpn(Map<String, String> params) {
        if (!vnpayPaymentService.verifySecureHash(params)) {
            return new VnpayIpnResponse("97", "Invalid checksum");
        }
        Payment payment = paymentRepository.findByProviderTxnRefWithBooking(params.get("vnp_TxnRef"))
                .orElse(null);
        if (payment == null) {
            return new VnpayIpnResponse("01", "Order not found");
        }
        if (!amountMatches(payment, params.get("vnp_Amount"))) {
            return new VnpayIpnResponse("04", "Invalid amount");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return new VnpayIpnResponse("02", "Order already confirmed");
        }

        payment.setProviderTransactionNo(params.get("vnp_TransactionNo"));
        payment.setTransactionId(params.get("vnp_TransactionNo"));
        payment.setRawResponse(new TreeMap<>(params));

        Booking booking = payment.getBooking();
        String transactionStatus = params.get("vnp_TransactionStatus");
        boolean successfulTransaction = "00".equals(params.get("vnp_ResponseCode"))
                && (transactionStatus == null || transactionStatus.isBlank() || "00".equals(transactionStatus));
        if (successfulTransaction) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(Instant.now());
            booking.setStatus(BookingStatus.PENDING);
            if (booking.getDiscountCodeId() != null) {
                discountCodeRepository.incrementUsedCount(booking.getDiscountCodeId());
            }
            notificationService.bookingStatusChanged(booking);
            return new VnpayIpnResponse("00", "Confirm success");
        }

        payment.setStatus(PaymentStatus.FAILED);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        return new VnpayIpnResponse("00", "Confirm success");
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(Long guestId, Long bookingId) {
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (!payment.getBooking().getGuest().getId().equals(guestId)) {
            throw new ResourceNotFoundException("Payment not found");
        }
        return PaymentStatusResponse.builder()
                .bookingId(payment.getBooking().getId())
                .bookingStatus(payment.getBooking().getStatus())
                .paymentStatus(payment.getStatus())
                .transactionId(payment.getTransactionId())
                .providerTransactionNo(payment.getProviderTransactionNo())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatusByTxnRef(Long guestId, String txnRef) {
        Payment payment = paymentRepository.findByProviderTxnRefWithBooking(txnRef)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if(!payment.getBooking().getGuest().getId().equals(guestId))
            throw new ResourceNotFoundException("Payment not found");
        return toStatusResponse(payment);
    }

    private PaymentStatusResponse toStatusResponse(Payment payment) {
        return PaymentStatusResponse.builder()
                .bookingId(payment.getBooking().getId())
                .bookingStatus(payment.getBooking().getStatus())
                .paymentStatus(payment.getStatus())
                .transactionId(payment.getTransactionId())
                .providerTransactionNo(payment.getProviderTransactionNo())
                .build();
    }

    private boolean amountMatches(Payment payment, String rawAmount) {
        try {
            BigDecimal expected = payment.getAmount().setScale(0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            return expected.compareTo(new BigDecimal(rawAmount)) == 0;
        } catch (NumberFormatException exception) {
            throw new BusinessException("ERR_VNPAY_AMOUNT", "VNPay amount is invalid.");
        }
    }

}
