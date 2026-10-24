package com.stayhub.common.validation;

import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.ConflictException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/** Shared HCM date policy for search, offer preview, quote and booking creation. */
public final class StayDatePolicy {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime SAME_DAY_CUTOFF = LocalTime.of(20, 0);

    private StayDatePolicy() {}

    public static int validate(LocalDate checkIn, LocalDate checkOut, ZonedDateTime now) {
        if (checkIn == null || checkOut == null || !checkIn.isBefore(checkOut))
            throw new BusinessException("ERR_INVALID_DATES", "Ngày nhận phòng phải trước ngày trả phòng.");
        LocalDate today = now.withZoneSameInstant(ZONE).toLocalDate();
        if (checkIn.isBefore(today))
            throw new BusinessException("ERR_INVALID_DATES", "Ngày nhận phòng không thể ở trong quá khứ.");
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > 30)
            throw new BusinessException("ERR_INVALID_DATES", "Thời gian lưu trú tối đa là 30 đêm.");
        if (checkIn.equals(today) && !now.withZoneSameInstant(ZONE).toLocalTime().isBefore(SAME_DAY_CUTOFF))
            throw new ConflictException("ERR_SAME_DAY_CUTOFF", "Same-day bookings must be created before 20:00 local time.");
        return Math.toIntExact(nights);
    }
}
