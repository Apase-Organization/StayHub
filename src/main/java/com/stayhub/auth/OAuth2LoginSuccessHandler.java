package com.stayhub.auth;

import com.stayhub.user.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        if (authentication instanceof OAuth2AuthenticationToken oauth2Token
                && oauth2Token.getPrincipal() instanceof OAuth2User oauth2User) {
            User user = customOAuth2UserService.upsertGoogleUser(oauth2User.getAttributes());
            UserPrincipal principal = UserPrincipal.create(user, oauth2User.getAttributes(), "sub");
            OAuth2AuthenticationToken localAuthentication = new OAuth2AuthenticationToken(
                    principal,
                    principal.getAuthorities(),
                    oauth2Token.getAuthorizedClientRegistrationId()
            );
            localAuthentication.setDetails(oauth2Token.getDetails());
            SecurityContextHolder.getContext().setAuthentication(localAuthentication);
        }

        setDefaultTargetUrl("/");
        // Honor the saved request so OAuth login from a protected deep link continues there.
        setAlwaysUseDefaultTargetUrl(false);
        super.onAuthenticationSuccess(request, response, SecurityContextHolder.getContext().getAuthentication());
    }
}
