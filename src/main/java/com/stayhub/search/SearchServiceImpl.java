package com.stayhub.search;

import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.validation.StayDatePolicy;
import com.stayhub.property.PropertyMapper;
import com.stayhub.property.dto.PropertySummary;
import com.stayhub.search.dto.SearchCriteria;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchServiceImpl implements SearchService {

    private final SearchRepository searchRepository;
    private final PropertyMapper propertyMapper;

    @Override
    public Page<PropertySummary> search(SearchCriteria criteria, Pageable pageable) {
        validateDates(criteria);
        return searchRepository.search(criteria, pageable)
                .map(property -> {
                    PropertySummary summary = propertyMapper.toSummary(property);
                    if (summary != null && criteria != null && criteria.getCheckIn() != null && criteria.getCheckOut() != null) {
                        int nights = (int) ChronoUnit.DAYS.between(criteria.getCheckIn(), criteria.getCheckOut());
                        summary.setNights(nights);
                        if (summary.getPricePerNight() != null) {
                            summary.setTotalStay(summary.getPricePerNight().multiply(BigDecimal.valueOf(nights)));
                        }
                    }
                    return summary;
                });
    }

    private void validateDates(SearchCriteria criteria) {
        if (criteria == null) {
            return;
        }
        LocalDate checkIn = criteria.getCheckIn();
        LocalDate checkOut = criteria.getCheckOut();
        if (checkIn == null && checkOut == null) {
            return;
        }
        if (checkIn == null || checkOut == null) {
            throw new BusinessException("ERR_INVALID_DATES", "Cả ngày nhận phòng và ngày trả phòng đều phải được cung cấp cùng nhau.");
        }
        StayDatePolicy.validate(checkIn, checkOut, ZonedDateTime.now(StayDatePolicy.ZONE));
    }
}
