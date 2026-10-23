package com.stayhub.config;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import com.stayhub.common.exception.ApiExceptionHandler;
import com.stayhub.common.exception.MvcExceptionHandler;
import com.stayhub.common.exception.ResourceNotFoundException;
import com.stayhub.common.security.ApiAccessDeniedHandler;
import com.stayhub.common.security.ApiAuthenticationEntryPoint;
import com.stayhub.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AdminController.class, AdminRestController.class})
@Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class,
        ApiExceptionHandler.class, MvcExceptionHandler.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private AdminService adminService;

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
}
