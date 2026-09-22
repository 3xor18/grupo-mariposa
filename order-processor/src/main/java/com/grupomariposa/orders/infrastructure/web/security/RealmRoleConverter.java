package com.grupomariposa.orders.infrastructure.web.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class RealmRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String REALM_ACCESS = "realm_access";
    public static final String ROLES = "roles";
    public static final String ROLE_PREFIX = "ROLE_";
    private static final String PREFERRED_USERNAME = "preferred_username";

    @Override
    public AbstractAuthenticationToken convert(final Jwt jwt) {
        final String principal = jwt.hasClaim(PREFERRED_USERNAME)
                ? jwt.getClaimAsString(PREFERRED_USERNAME) : jwt.getSubject();
        return new JwtAuthenticationToken(jwt, authorities(jwt), principal);
    }

    static Collection<GrantedAuthority> authorities(final Jwt jwt) {
        final Object realmAccess = jwt.getClaims().get(REALM_ACCESS);
        if (!(realmAccess instanceof Map<?, ?> access)
                || !(access.get(ROLES) instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .toList();
    }
}
