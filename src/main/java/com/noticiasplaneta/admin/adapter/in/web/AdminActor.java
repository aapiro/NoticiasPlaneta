package com.noticiasplaneta.admin.adapter.in.web;

import com.noticiasplaneta.shared.domain.Actor;
import com.noticiasplaneta.shared.domain.ActorType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

/** Traduce la sesión de Spring Security al Actor del dominio editorial. */
final class AdminActor {

    private AdminActor() {
    }

    static Actor from(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .collect(Collectors.toSet());

        return new Actor(authentication.getName(), ActorType.HUMAN, roles, Set.of());
    }
}
