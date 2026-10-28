package com.stayhub.booking;

public enum BookingStatus {
    PENDING,
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    REJECTED,
    COMPLETED;

    public String getDisplayName() {
        return switch (this) {
            case PENDING -> "Chờ xác nhận";
            case PENDING_PAYMENT -> "Chờ thanh toán";
            case CONFIRMED -> "Đã xác nhận";
            case CANCELLED -> "Đã hủy";
            case REJECTED -> "Đã từ chối";
            case COMPLETED -> "Đã hoàn tất";
        };
    }
}
