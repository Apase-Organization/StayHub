package com.stayhub.common.security;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.user.UserRepository;
import com.stayhub.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rechecks persisted account state so a session cannot outlive an Admin lock. */
@Component
@RequiredArgsConstructor
public class LockedAccountSessionFilter extends OncePerRequestFilter {
    private final UserRepository users;

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        Authentication authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null&&authentication.isAuthenticated()&&authentication.getPrincipal() instanceof UserPrincipal principal) {
            boolean active=users.findById(principal.getId()).map(user->user.getStatus()==UserStatus.ACTIVE).orElse(false);
            if(!active) {
                SecurityContextHolder.clearContext();
                var session=request.getSession(false);
                if(session!=null) session.invalidate();
            }
        }
        // Public verified payment callbacks and the read-only return page still pass
        // through normal security rules after an old account session is removed.
        chain.doFilter(request,response);
    }
}
