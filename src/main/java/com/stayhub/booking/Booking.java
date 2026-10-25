package com.stayhub.booking;

import com.stayhub.common.entity.BaseEntity;
import com.stayhub.payment.Payment;
import com.stayhub.property.Property;
import com.stayhub.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    private User guest;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(nullable = false)
    private Integer guests;

    @Column(name = "nightly_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal nightlyPrice;

    @Column(name = "cleaning_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal cleaningFee;

    @Column(name = "service_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal serviceFee;

    @Column(name = "subtotal_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalPrice;

    @Column(name = "discount_code_id")
    private Long discountCodeId;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private Long completedBy;

    @OneToOne(mappedBy = "booking", fetch = FetchType.LAZY)
    private Payment payment;

    @PrePersist
    void applyPriceDefaults() {
        if (subtotalPrice == null) {
            subtotalPrice = totalPrice;
        }
        if (discountAmount == null) {
            discountAmount = BigDecimal.ZERO;
        }
    }
}
