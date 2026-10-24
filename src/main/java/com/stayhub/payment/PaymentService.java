package com.stayhub.payment;

import com.stayhub.booking.Booking;
import com.stayhub.payment.dto.PaymentStatusResponse;
import com.stayhub.payment.dto.VnpayIpnResponse;
import java.util.Map;

public interface PaymentService {
    Payment createSuccessfulPayment(Booking booking);

    Payment createPendingVnpayPayment(Booking booking);

    String buildVnpayCheckoutUrl(Payment payment, String ipAddress);

    VnpayIpnResponse handleVnpayIpn(Map<String, String> params);

    PaymentStatusResponse getPaymentStatus(Long guestId, Long bookingId);

    PaymentStatusResponse getPaymentStatusByTxnRef(Long guestId, String txnRef);
}
