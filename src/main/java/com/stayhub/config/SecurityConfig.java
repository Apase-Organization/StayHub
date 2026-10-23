package com.stayhub.config;

import com.stayhub.common.security.ApiAccessDeniedHandler;
import com.stayhub.common.security.ApiAuthenticationEntryPoint;
import com.stayhub.auth.CustomOAuth2UserService;
import com.stayhub.auth.OAuth2LoginSuccessHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
                                           ApiAccessDeniedHandler apiAccessDeniedHandler,
                                           ObjectProvider<CustomOAuth2UserService> customOAuth2UserService,
                                           ObjectProvider<OAuth2LoginSuccessHandler> oauth2LoginSuccessHandler) throws Exception {
        AntPathRequestMatcher apiRequestMatcher = new AntPathRequestMatcher("/api/**");

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/home", "/css/**", "/js/**", "/images/**", "/uploads/**").permitAll()
                .requestMatchers("/login", "/register", "/register/verify", "/payments/vnpay/return").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/properties", "/properties/*",
                        "/api/v1/properties", "/api/v1/properties/*",
                        "/api/v1/amenities",
                        "/api/v1/payments/vnpay/ipn",
                        "/api/v1/payments/vnpay/status").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/bookings/check-availability").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/properties/*/availability").permitAll()
                .requestMatchers("/admin/**", "/api/v1/admin/**").hasRole("ADMIN")                
                .requestMatchers("/host/**", "/api/v1/host/**", "/api/v1/bookings/host/**").hasAnyRole("HOST", "ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .oauth2Login(oauth2 -> {
                oauth2.loginPage("/login")
                    .defaultSuccessUrl("/", true)
                    .userInfoEndpoint(userInfo -> customOAuth2UserService.ifAvailable(userInfo::userService));
                oauth2LoginSuccessHandler.ifAvailable(oauth2::successHandler);
            })
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(apiAuthenticationEntryPoint, apiRequestMatcher)
                .defaultAccessDeniedHandlerFor(apiAccessDeniedHandler, apiRequestMatcher)
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new NegatedRequestMatcher(apiRequestMatcher)
                )
                .defaultAccessDeniedHandlerFor(
                    new AccessDeniedHandlerImpl(),
                    new NegatedRequestMatcher(apiRequestMatcher)
                )
            );

        return http.build();
    }
}
