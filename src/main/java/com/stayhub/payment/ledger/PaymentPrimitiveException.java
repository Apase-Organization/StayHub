package com.stayhub.payment.ledger;

/** Internal business failure. No public financial mutation route is installed. */
public final class PaymentPrimitiveException extends RuntimeException {
    private final String errorCode;
    public PaymentPrimitiveException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }
    public String errorCode() { return errorCode; }
    public int status() {
        return switch(errorCode) {
            case "ERR_INVALID_REQUEST", "ERR_NONPOSITIVE_BOOKING_AMOUNT", "ERR_SELF_BOOKING" -> 400;
            case "ERR_FORBIDDEN" -> 403;
            case "ERR_NOT_FOUND" -> 404;
            default -> 409;
        };
    }
}
