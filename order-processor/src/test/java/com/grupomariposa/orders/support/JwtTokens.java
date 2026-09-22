package com.grupomariposa.orders.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class JwtTokens {

    public static final String ISSUER = "http://issuer.test/realms/mariposa";
    public static final String AUDIENCE = "order-processor";
    public static final String JWKS_PATH = "/jwks";
    private static final int KEY_SIZE = 2048;
    private static final long LIFETIME_SECONDS = 300;
    private static final RSAKey KEY = generate();

    private JwtTokens() {
    }

    public static void publishKeys(final WireMockServer server) {
        server.stubFor(WireMock.get(JWKS_PATH).willReturn(WireMock.okJson(
                new JWKSet(KEY.toPublicJWK()).toString())));
    }

    public static String token(final String audience, final List<String> roles) {
        final Instant now = Instant.now();
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("user-" + roles.size())
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(LIFETIME_SECONDS)))
                .claim("preferred_username", "tester")
                .claim("realm_access", Map.of("roles", roles))
                .build();
        final SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KEY.getKeyID()).build(), claims);
        try {
            jwt.sign(new RSASSASigner(KEY));
        } catch (JOSEException failure) {
            throw new IllegalStateException(failure);
        }
        return jwt.serialize();
    }

    private static RSAKey generate() {
        try {
            return new RSAKeyGenerator(KEY_SIZE).keyID("it-key").generate();
        } catch (JOSEException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
