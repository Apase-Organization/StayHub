package com.stayhub.config;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.stayhub.admin.AdminController;
import com.stayhub.admin.AdminRestController;
import com.stayhub.admin.AdminService;
import com.stayhub.payment.PrivateEvidenceController;
import com.stayhub.payment.ledger.PrivateEvidenceService;
import com.stayhub.common.exception.ApiExceptionHandler;
import com.stayhub.common.exception.MvcExceptionHandler;
import com.stayhub.common.exception.ResourceNotFoundException;
import com.stayhub.common.security.ApiAccessDeniedHandler;
import com.stayhub.common.security.ApiAuthenticationEntryPoint;
import com.stayhub.common.security.LockedAccountSessionFilter;
import com.stayhub.auth.UserPrincipal;
import com.stayhub.auth.CustomUserDetailsService;
import com.stayhub.user.User;
import com.stayhub.user.UserRepository;
import com.stayhub.user.UserRole;
import com.stayhub.user.UserStatus;
import com.stayhub.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;

@WebMvcTest(controllers = {AdminController.class, AdminRestController.class,PrivateEvidenceController.class})
@Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class,
        ApiExceptionHandler.class, MvcExceptionHandler.class,LockedAccountSessionFilter.class,CustomUserDetailsService.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private AdminService adminService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PrivateEvidenceService privateEvidenceService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Environment environment;

    @Test
    void apiPostWithoutCsrfReturnsJsonForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/1/lock"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("ERR_CSRF"));
    }

    @Test
    void anonymousApiWithCsrfReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/1/lock").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ERR_UNAUTHORIZED"));
    }

    @Test
    void wrongRoleReturnsJsonForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/1/lock")
                        .with(user("guest").roles("GUEST"))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ERR_FORBIDDEN"));
    }

    @Test
    void onlyPropertyReadsArePublic() throws Exception {
        mockMvc.perform(post("/api/v1/properties").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ERR_UNAUTHORIZED"));
    }

    @Test
    void publicReadOnlyOfferSearchStillRequiresCsrfForPost() throws Exception {
        mockMvc.perform(post("/api/v1/properties/1/availability"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ERR_CSRF"));
    }

    @Test
    void readOnlyOfferSearchDoesNotRequireLogin() throws Exception {
        mockMvc.perform(post("/api/v1/properties/1/availability").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void providerIpnDoesNotRequireGuestSession() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/payments/vnpay/ipn").param("vnp_TxnRef", "missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void privateEvidenceIndexRequiresAuthenticatedBookingParticipant() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/1/private-evidence").param("purpose","DIRECT_RECEIPT"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("ERR_UNAUTHORIZED"));
    }

    @Test
    void paymentStatusPollingRequiresAuthentication() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/payments/vnpay/status").param("txnRef", "opaque-ref"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ERR_UNAUTHORIZED"));
    }

    @Test
    void bookingPageIsNotCoveredByPublicPropertyMatcher() throws Exception {
        mockMvc.perform(get("/properties/1/book"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void adminWithCsrfCanLockUser() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/1/lock")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService).lockUser(1L);
    }

    @Test
    void apiBusinessErrorsUseJsonEnvelope() throws Exception {
        doThrow(new ResourceNotFoundException("User not found"))
                .when(userService).lockUser(9L);

        mockMvc.perform(post("/api/v1/admin/users/9/lock")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ERR_NOT_FOUND"));
    }

    @Test
    void anonymousMvcRequestStillRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void formLoginReturnsToTheProtectedPageInsteadOfAlwaysSendingUserHome() throws Exception {
        User account=User.builder().email("return-to-request@example.test").fullName("Admin")
                .passwordHash(passwordEncoder.encode("valid-password")).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        account.setId(501L);
        org.mockito.Mockito.when(userRepository.findByEmail("return-to-request@example.test"))
                .thenReturn(Optional.of(account));
        var blocked=mockMvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"))
                .andReturn();
        MockHttpSession session=(MockHttpSession)blocked.getRequest().getSession(false);
        org.assertj.core.api.Assertions.assertThat(session).isNotNull();
        mockMvc.perform(post("/login").session(session).with(csrf())
                        .param("username","return-to-request@example.test").param("password","valid-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/admin/users**"));
    }

    @Test
    void localSessionUsesLongLivedHttpOnlyLaxCookieForLocalProviderReturn() {
        org.assertj.core.api.Assertions.assertThat(environment.getProperty("server.servlet.session.timeout")).isEqualTo("8h");
        org.assertj.core.api.Assertions.assertThat(environment.getProperty("server.servlet.session.cookie.http-only",Boolean.class)).isTrue();
        org.assertj.core.api.Assertions.assertThat(environment.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
        org.assertj.core.api.Assertions.assertThat(environment.getProperty("server.servlet.session.cookie.secure",Boolean.class)).isFalse();
    }

    @Test
    void mvcErrorsRenderHtmlErrorPage() throws Exception {
        doThrow(new ResourceNotFoundException("User not found"))
                .when(userService).getUsers(0, 20);

        mockMvc.perform(get("/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/error"));
    }

    @Test
    void adminMvcPageRendersForAdmin() throws Exception {
        org.mockito.Mockito.when(userService.getUsers(0, 20)).thenReturn(Page.empty());

        mockMvc.perform(get("/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"));
    }

    @Test
    void persistedLockInvalidatesAnExistingAdminSessionButDoesNotBlockPublicProviderCallback() throws Exception {
        User user=User.builder().email("locked-admin@example.test").fullName("Locked Admin")
                .passwordHash("test").role(UserRole.ADMIN).status(UserStatus.LOCKED).build();
        user.setId(99L);
        org.mockito.Mockito.when(userRepository.findById(99L)).thenReturn(Optional.of(user));
        UserPrincipal principal=UserPrincipal.create(user);
        var auth=new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities());
        mockMvc.perform(post("/api/v1/admin/users/1/lock").with(authentication(auth)).with(csrf()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("ERR_UNAUTHORIZED"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/payments/vnpay/ipn").param("vnp_TxnRef","missing")
                        .with(authentication(auth)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/refunds/1/approve").with(authentication(auth)).with(csrf()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("ERR_UNAUTHORIZED"));
    }
}
