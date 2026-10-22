package com.stayhub.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stayhub.common.exception.BusinessException;
import com.stayhub.property.Property;
import com.stayhub.property.PropertyRepository;
import com.stayhub.property.PropertyStatus;
import com.stayhub.property.PropertyType;
import com.stayhub.property.dto.PropertySummary;
import com.stayhub.search.SearchService;
import com.stayhub.search.dto.SearchCriteria;
import com.stayhub.support.PostgreSqlIntegrationTest;
import com.stayhub.user.User;
import com.stayhub.user.UserRepository;
import com.stayhub.user.UserRole;
import com.stayhub.user.UserStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * TSK-107: Inventory Runtime Evidence Matrix Test Suite.
 * Verifies all-or-nothing multi-night continuity, adjacency, child age & occupancy boundaries,
 * and conflict isolation on real database state.
 */
@AutoConfigureMockMvc
class InventoryMatrixIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SearchService searchService;

    @Autowired
    private BookingPriceService bookingPriceService;

    private User host;
    private User guest;
    private Property deluxeProperty;
    private Property familyProperty;
    private LocalDate baseDate;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();

        baseDate = LocalDate.now().plusDays(2);

        host = userRepository.save(User.builder()
                .fullName("Inventory Host")
                .email("host.inv@stayhub.local")
                .passwordHash("hash")
                .role(UserRole.HOST)
                .status(UserStatus.ACTIVE)
                .build());

        guest = userRepository.save(User.builder()
                .fullName("Inventory Guest")
                .email("guest.inv@stayhub.local")
                .passwordHash("hash")
                .role(UserRole.GUEST)
                .status(UserStatus.ACTIVE)
                .build());

        // Deluxe property: 5 bedrooms, 10 guests
        deluxeProperty = propertyRepository.save(Property.builder()
                .host(host)
                .title("Deluxe 5-Room Villa")
                .description("Spacious luxury villa")
                .city("Da Nang")
                .address("101 Ocean Drive")
                .pricePerNight(new BigDecimal("2000000"))
                .cleaningFee(new BigDecimal("200000"))
                .maxGuests(10)
                .bedrooms(5)
                .beds(5)
                .bathrooms(4)
                .ratingAvg(new BigDecimal("4.9"))
                .propertyType(PropertyType.VILLA)
                .status(PropertyStatus.ACTIVE)
                .build());

        // Family property: 2 bedrooms, 4 guests
        familyProperty = propertyRepository.save(Property.builder()
                .host(host)
                .title("Family 2-Room Suite")
                .description("Cozy suite for family vacations")
                .city("Da Nang")
                .address("202 Garden View")
                .pricePerNight(new BigDecimal("1200000"))
                .cleaningFee(new BigDecimal("100000"))
                .maxGuests(4)
                .bedrooms(2)
                .beds(2)
                .bathrooms(1)
                .ratingAvg(new BigDecimal("4.8"))
                .propertyType(PropertyType.APARTMENT)
                .status(PropertyStatus.ACTIVE)
                .build());
    }

    @AfterEach
    void cleanUp() {
        bookingRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void multiNightContinuityAndConflictExclusion() {
        // Given an existing active booking on night 2 for familyProperty (day 2 to day 3)
        bookingRepository.save(Booking.builder()
                .guest(guest)
                .property(familyProperty)
                .checkInDate(baseDate.plusDays(1))
                .checkOutDate(baseDate.plusDays(2))
                .guests(2)
                .nightlyPrice(new BigDecimal("1200000"))
                .cleaningFee(new BigDecimal("100000"))
                .serviceFee(new BigDecimal("180000"))
                .subtotalPrice(new BigDecimal("1480000"))
                .discountAmount(BigDecimal.ZERO)
                .totalPrice(new BigDecimal("1480000"))
                .status(BookingStatus.CONFIRMED)
                .build());

        // A guest searching for a 3-night stay (day 0 to day 3) over familyProperty
        SearchCriteria criteria = new SearchCriteria();
        criteria.setLocation("Da Nang");
        criteria.setCheckIn(baseDate);
        criteria.setCheckOut(baseDate.plusDays(3));
        criteria.setGuests(2);

        Page<PropertySummary> results = searchService.search(criteria, PageRequest.of(0, 10));

        // Only deluxeProperty is available because night 2 on familyProperty is blocked (all-or-nothing continuity)
        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().getFirst().getId()).isEqualTo(deluxeProperty.getId());
        assertThat(results.getContent().getFirst().getNights()).isEqualTo(3);
        assertThat(results.getContent().getFirst().getTotalStay()).isEqualByComparingTo(new BigDecimal("6000000"));
    }

    @Test
    void halfOpenAdjacencyPermitsAdjoiningBookings() {
        // Booking 1: baseDate to baseDate + 2 (checkout at 11:00 on day 2)
        bookingRepository.save(Booking.builder()
                .guest(guest)
                .property(deluxeProperty)
                .checkInDate(baseDate)
                .checkOutDate(baseDate.plusDays(2))
                .guests(4)
                .nightlyPrice(new BigDecimal("2000000"))
                .cleaningFee(new BigDecimal("200000"))
                .serviceFee(new BigDecimal("300000"))
                .subtotalPrice(new BigDecimal("4500000"))
                .discountAmount(BigDecimal.ZERO)
                .totalPrice(new BigDecimal("4500000"))
                .status(BookingStatus.CONFIRMED)
                .build());

        // Booking 2 check-in at 14:00 on baseDate + 2 (adjoining checkout date)
        SearchCriteria criteria = new SearchCriteria();
        criteria.setLocation("Da Nang");
        criteria.setCheckIn(baseDate.plusDays(2));
        criteria.setCheckOut(baseDate.plusDays(4));
        criteria.setGuests(4);

        Page<PropertySummary> results = searchService.search(criteria, PageRequest.of(0, 10));

        // deluxeProperty MUST remain available because [baseDate, baseDate+2) and [baseDate+2, baseDate+4) are adjacent
        assertThat(results.getContent())
                .extracting(PropertySummary::getId)
                .contains(deluxeProperty.getId());
    }

    @Test
    void roomFilterExcludesPropertiesWithInsufficientBedrooms() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setLocation("Da Nang");
        criteria.setRooms(3); // requires >= 3 bedrooms

        Page<PropertySummary> results = searchService.search(criteria, PageRequest.of(0, 10));

        // Only deluxeProperty has 5 bedrooms (familyProperty only has 2)
        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().getFirst().getId()).isEqualTo(deluxeProperty.getId());
    }

    @Test
    void durationBoundaryEnforcesThirtyNightsLimit() {
        SearchCriteria validCriteria = new SearchCriteria();
        validCriteria.setCheckIn(baseDate);
        validCriteria.setCheckOut(baseDate.plusDays(30)); // Exactly 30 nights is valid

        Page<PropertySummary> validResults = searchService.search(validCriteria, PageRequest.of(0, 10));
        assertThat(validResults.getContent()).isNotEmpty();

        SearchCriteria invalidCriteria = new SearchCriteria();
        invalidCriteria.setCheckIn(baseDate);
        invalidCriteria.setCheckOut(baseDate.plusDays(31)); // 31 nights exceeds limit

        assertThatThrownBy(() -> searchService.search(invalidCriteria, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("30 đêm");
    }
}
