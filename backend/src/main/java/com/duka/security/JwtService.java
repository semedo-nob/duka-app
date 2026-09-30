package com.duka.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.duka.config.DukaProperties;
import com.duka.domain.UserAccount;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final Algorithm algorithm;
    private final int ttlMinutes;

    public JwtService(DukaProperties properties) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.length() < 16) {
            throw new IllegalStateException("DUKA_JWT_SECRET must be set and at least 16 characters");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.ttlMinutes = properties.getJwt().getTtlMinutes();
    }

    public String issue(UserAccount user, String sessionId) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer("duka")
                .withSubject(user.getId().toString())
                .withClaim("kind", "business")
                .withClaim("sid", sessionId)
                .withClaim("role", user.getRole().name())
                .withClaim("name", user.getName())
                .withClaim("businessId", user.getBusinessId())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(ttlMinutes * 60L)))
                .sign(algorithm);
    }

    public String issuePlatform(Long id, String name, String role) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer("duka")
                .withSubject(id.toString())
                .withClaim("kind", "platform")
                .withClaim("role", role)
                .withClaim("name", name)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(ttlMinutes * 60L)))
                .sign(algorithm);
    }

    public int ttlMinutes() {
        return ttlMinutes;
    }

    public DecodedJWT verify(String token) {
        return JWT.require(algorithm).withIssuer("duka").build().verify(token);
    }
}
