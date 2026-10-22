package com.stayhub.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stayhub.booking.Booking;
import com.stayhub.booking.BookingRepository;
import com.stayhub.booking.BookingStatus;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.property.Property;
import com.stayhub.property.PropertyRepository;
import com.stayhub.property.PropertyStatus;
import com.stayhub.property.PropertyType;
import com.stayhub.property.dto.PropertySummary;
import com.stayhub.search.dto.SearchCriteria;
import com.stayhub.support.PostgreSqlIntegrationTest;
import com.stayhub.user.User;
import com.stayhub.user.UserRepository;
import com.stayhub.user.UserRole;
import com.stayhub.user.UserStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class SearchAvailabilityIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    private Property availableProperty;
    private Property bookedProperty;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();

        today = LocalDate.now();

        User host = userRepository.save(User.builder()
                .fullName("Test Host")
                .email("host.search@example.com")
                .passwordHash("hash")
                .role(UserRole.HOST)
                .status(UserStatus.ACTIVE)
                .build());

        User guest = userRepository.save(User.builder()
                .fullName("Test Guest")
                .email("guest.search@example.com")
                .passwordHash("hash")
                .role(UserRole.GUEST)
                .status(UserStatus.ACTIVE)
                .build());

        availableProperty = propertyRepository.save(Property.builder()
                .host(host)
                .title("Sunny Apartment")
                .description("Always free")
                .city("Da Nang")
                .address("100 Beach Road")
                .pricePerNight(BigDecimal.valueOf(500000))
                .cleaningFee(BigDecimal.valueOf(50000))
                .maxGuests(4)
                .bedrooms(2)
                .beds(2)
                .bathrooms(1)
                .propertyType(PropertyType.APARTMENT)
                .status(PropertyStatus.ACTIVE)
                .ratingAvg(BigDecimal.valueOf(4.8))
                .build());

        bookedProperty = propertyRepository.save(Property.builder()
                .host(host)
                .title("Booked Villa")
                .description("Has conflicting booking")
                .city("Da Nang")
                .address("200 Beach Road")
                .pricePerNight(BigDecimal.valueOf(1200000))
                .cleaningFee(BigDecimal.valueOf(100000))
                .maxGuests(6)
                .bedrooms(3)
                .beds(3)
                .bathrooms(2)
                .propertyType(PropertyType.VILLA)
                .status(PropertyStatus.ACTIVE)
                .ratingAvg(BigDecimal.valueOf(4.9))
                .build());

        // Booking on bookedProperty from today+5 to today+10
        bookingRepository.save(Booking.builder()
                .property(bookedProperty)
                .guest(guest)
                .checkInDate(today.plusDays(5))
                .checkOutDate(today.plusDays(10))
                .guests(2)
                .nightlyPrice(BigDecimal.valueOf(1200000))
                .cleaningFee(BigDecimal.valueOf(100000))
                .serviceFee(BigDecimal.valueOf(50000))
                .subtotalPrice(BigDecimal.valueOf(6150000))
                .totalPrice(BigDecimal.valueOf(6150000))
                .status(BookingStatus.CONFIRMED)
                .build());
    }

    @Test
    void searchExcludesPropertiesWithConflictingBookingsDuringStay() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setLocation("Da Nang");
        criteria.setCheckIn(today.plusDays(6));
        criteria.setCheckOut(today.plusDays(8));

        Page<PropertySummary> results = searchService.search(criteria, PageRequest.of(0, 10));

        assertThat(results.getContent())
                .extracting(PropertySummary::getId)
                .contains(availableProperty.getId())
                .doesNotContain(bookedProperty.getId());

        // Check basis calculation: 2 nights
        PropertySummary item = results.getContent().getFirst();
        assertThat(item.getNights()).isEqualTo(2);
        assertThat(item.getTotalStay()).isEqualByComparingTo(BigDecimal.valueOf(1000000));
    }

    @Test
    void searchAllowsAdjacentDatesWithoutConflict() {
        // Search check-in is exactly previous booking check-out (today+10)
        SearchCriteria criteria = new SearchCriteria();
        criteria.setLocation("Da Nang");
        criteria.setCheckIn(today.plusDays(10));
        criteria.setCheckOut(today.plusDays(12));

        Page<PropertySummary> results = searchService.search(criteria, PageRequest.of(0, 10));

        // Both properties should be available because checkout day 11:00 allows new checkin 14:00
        assertThat(results.getContent())
                .extracting(PropertySummary::getId)
                .contains(availableProperty.getId(), bookedProperty.getId());
    }

    @Test
    void searchWithoutDatesReturnsIndicativePriceWithoutLiveStock() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setLocation("Da Nang");

        Page<PropertySummary> results = searchService.search(criteria, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(2);
        assertThat(results.getContent().getFirst().getNights()).isNull();
        assertThat(results.getContent().getFirst().getTotalStay()).isNull();
    }

    @Test
    void searchWithPastDatesThrowsInvalidDates() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setCheckIn(today.minusDays(1));
        criteria.setCheckOut(today.plusDays(2));

        assertThatThrownBy(() -> searchService.search(criteria, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quá khứ");
    }

    @Test
    void searchWithDurationOverThirtyNightsThrowsInvalidDates() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setCheckIn(today.plusDays(1));
        criteria.setCheckOut(today.plusDays(35));

        assertThatThrownBy(() -> searchService.search(criteria, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("30 đêm");
    }

    @AfterEach
    void cleanUp() {
        bookingRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();
    }
}
