package com.stayhub.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.ConflictException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class StayDatePolicyTest {
    private static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test void sameDayCutoffUsesHcmTimeAtTheExactBoundary() {
        LocalDate stayDate = LocalDate.of(2030, 12, 10);
        assertThat(StayDatePolicy.validate(stayDate, stayDate.plusDays(1),
                ZonedDateTime.of(stayDate, LocalTime.of(19, 59), HCM))).isEqualTo(1);
        assertThatThrownBy(() -> StayDatePolicy.validate(stayDate, stayDate.plusDays(1),
                ZonedDateTime.of(stayDate, LocalTime.of(20, 0), HCM)))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("ERR_SAME_DAY_CUTOFF");
    }

    @Test void datePolicyAllowsOneAndThirtyNightsAndRejectsZeroAndThirtyOne() {
        LocalDate stayDate = LocalDate.of(2030, 12, 10);
        ZonedDateTime now = ZonedDateTime.of(stayDate.minusDays(1), LocalTime.NOON, HCM);
        assertThat(StayDatePolicy.validate(stayDate, stayDate.plusDays(1), now)).isEqualTo(1);
        assertThat(StayDatePolicy.validate(stayDate, stayDate.plusDays(30), now)).isEqualTo(30);
        assertThatThrownBy(() -> StayDatePolicy.validate(stayDate, stayDate, now)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> StayDatePolicy.validate(stayDate, stayDate.plusDays(31), now))
                .isInstanceOf(BusinessException.class).hasMessageContaining("30");
        assertThatThrownBy(() -> StayDatePolicy.validate(stayDate.minusDays(1), stayDate,
                ZonedDateTime.of(stayDate, LocalTime.NOON, HCM)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("quá khứ");
    }
}
