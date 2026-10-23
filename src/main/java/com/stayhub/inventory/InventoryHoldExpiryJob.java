package com.stayhub.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class InventoryHoldExpiryJob {
    private final InventoryBookingService bookings;

    @Scheduled(fixedDelayString = "${app.inventory.expiry-delay-ms:60000}")
    public void expireHolds() {
        bookings.findExpired(200).forEach(bookings::expire);
    }
}
