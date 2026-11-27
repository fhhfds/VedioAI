package com.vedioai.server.identity.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentActorProvider {

    public CurrentActor requireCurrentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new InsufficientAuthenticationException("需要登录");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CurrentActor actor) {
            return actor;
        }

        throw new InsufficientAuthenticationException("未受信的身份主体");
    }
}