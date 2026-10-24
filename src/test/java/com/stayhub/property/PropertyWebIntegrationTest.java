package com.stayhub.property;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.support.PostgreSqlIntegrationTest;
import com.stayhub.user.User;
import com.stayhub.user.UserRepository;
import com.stayhub.user.UserRole;
import com.stayhub.user.UserStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfigureMockMvc
class PropertyWebIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private PropertyImageRepository imageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private User host;
    private Property property;

    @BeforeEach
    void setUp() {
        cleanInventoryRows();
        imageRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();
        host = userRepository.save(User.builder()
                .email("web-host@example.com")
                .passwordHash("password-hash")
                .fullName("Web Host")
                .role(UserRole.HOST)
                .status(UserStatus.ACTIVE)
                .build());
        property = propertyRepository.save(Property.builder()
                .host(host)
                .title("Riverside Retreat")
                .description("A quiet place by the river.")
                .address("12 River Road")
                .city("Da Nang")
                .pricePerNight(new BigDecimal("1500000.00"))
                .cleaningFee(new BigDecimal("100000.00"))
                .maxGuests(4)
                .bedrooms(2)
                .beds(2)
                .bathrooms(2)
                .propertyType(PropertyType.VILLA)
                .status(PropertyStatus.ACTIVE)
                .ratingAvg(BigDecimal.ZERO)
                .build());
    }

    @AfterEach
    void cleanUp() {
        cleanInventoryRows();
        imageRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void cleanInventoryRows() {
        jdbc.update("DELETE FROM type_day_inventory WHERE room_type_id IN (SELECT id FROM room_types WHERE property_id IN (SELECT id FROM properties))");
        jdbc.update("DELETE FROM rate_plans WHERE property_id IN (SELECT id FROM properties)");
        jdbc.update("DELETE FROM room_types WHERE property_id IN (SELECT id FROM properties)");
        jdbc.update("UPDATE properties SET sale_mode=NULL WHERE sale_mode IS NOT NULL");
    }

    @Test
    void rendersPublicPropertyDetail() throws Exception {
        mockMvc.perform(get("/properties/{id}", property.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("property/property-detail"))
                .andExpect(model().attributeExists("property"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Riverside Retreat")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("room-occupancies")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Find eligible offers")));
    }

    @Test
    void publicOfferAvailabilityReturnsOnePropertyWithEligibleServerPrice() throws Exception {
        jdbc.update("UPDATE properties SET sale_mode='ROOM_BASED' WHERE id=?", property.getId());
        long typeId = jdbc.queryForObject("""
                INSERT INTO room_types(property_id,sale_mode,name,base_quantity,max_adults,max_occupants)
                VALUES (?,'ROOM_BASED','Deluxe',2,2,3) RETURNING id
                """, Long.class, property.getId());
        long planId = jdbc.queryForObject("""
                INSERT INTO rate_plans(property_id,room_type_id,name,policy_version,nightly_price)
                VALUES (?,?, 'Standard','s0-policy-v1',1500000) RETURNING id
                """, Long.class, property.getId(), typeId);
        jdbc.update("INSERT INTO type_day_inventory(room_type_id,stay_date,total) VALUES (?, '2030-12-10',2)", typeId);

        mockMvc.perform(post("/api/v1/properties/{id}/availability", property.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"checkIn":"2030-12-10","checkOut":"2030-12-11","currency":"VND",
                                 "paymentMode":"FULL_PREPAY","roomOccupancies":[{"adults":2,"childrenAges":[]}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.propertyId").value(property.getId()))
                .andExpect(jsonPath("$.data.liveAvailabilityChecked").value(true))
                .andExpect(jsonPath("$.data.offers[0].ratePlanId").value(planId))
                .andExpect(jsonPath("$.data.offers[0].availableQuantity").value(2))
                .andExpect(jsonPath("$.data.offers[0].totalStay").value(1600000));
    }

    @Test
    void rendersHostEditFormForOwner() throws Exception {
        UserPrincipal principal = UserPrincipal.create(host);
        var token = new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities());

        mockMvc.perform(get("/host/properties/{id}/edit", property.getId())
                        .with(authentication(token)))
                .andExpect(status().isOk())
                .andExpect(view().name("host/property-form"))
                .andExpect(model().attributeExists("property", "propertyRequest", "amenities"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Riverside Retreat")));
    }

    @Test
    void guestCannotAccessHostDashboardOrEditForm() throws Exception {
        User guest = userRepository.save(User.builder()
                .email("guest@example.com")
                .passwordHash("password-hash")
                .fullName("Guest User")
                .role(UserRole.GUEST)
                .status(UserStatus.ACTIVE)
                .build());
        UserPrincipal principal = UserPrincipal.create(guest);
        var token = new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities());

        mockMvc.perform(get("/host/properties")
                        .with(authentication(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/host/properties/{id}/edit", property.getId())
                        .with(authentication(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void hostApiRequiresCsrfProtection() throws Exception {
        UserPrincipal principal = UserPrincipal.create(host);
        var token = new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities());

        // Performing post/put/delete without CSRF token should return 403 Forbidden with ERR_CSRF
        mockMvc.perform(post("/api/v1/host/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Villa\"}")
                        .with(authentication(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ERR_CSRF"));
    }

    @Test
    void otherHostCannotAccessOrEditForeignProperty() throws Exception {
        User otherHost = userRepository.save(User.builder()
                .email("other-host@example.com")
                .passwordHash("password-hash")
                .fullName("Other Host")
                .role(UserRole.HOST)
                .status(UserStatus.ACTIVE)
                .build());
        UserPrincipal principal = UserPrincipal.create(otherHost);
        var token = new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities());

        // Attempting to edit foreign property should yield 404/not found as a safety measure
        mockMvc.perform(get("/host/properties/{id}/edit", property.getId())
                        .with(authentication(token)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/host/properties/{id}", property.getId())
                        .with(authentication(token)))
                .andExpect(status().isNotFound());
    }
}
