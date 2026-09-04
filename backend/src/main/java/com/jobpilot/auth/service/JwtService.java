package com.jobpilot.auth.service;

import com.jobpilot.auth.domain.UserEntity;
import com.jobpilot.common.exception.UnauthorizedException;
import com.jobpilot.common.security.JwtProperties;
import com.jobpilot.common.util.UlidGenerator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public static final String ACCESS = "access";
    public static final String REFRESH = "refresh";
    public static final String EXTENSION_ACCESS = "extension_access";
    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        if (properties.getJwtSecret() == null || properties.getJwtSecret().length() < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UserEntity user) {
        return createAccessToken(user, null);
    }

    public String createAccessToken(UserEntity user, String familyId) {
        return createToken(user, ACCESS, UlidGenerator.next(), familyId, properties.getAccessExpirationSeconds());
    }

    public String createRefreshToken(UserEntity user, String familyId) {
        return createToken(user, REFRESH, UlidGenerator.next(), familyId, properties.getRefreshExpirationSeconds());
    }

    public String createExtensionAccessToken(UserEntity user, String devicePublicId, int tokenVersion,
                                             List<String> scopes, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(user.getPublicId())
                .id(UlidGenerator.next())
                .claim("uid", user.getId())
                .claim("username", user.getUsername())
                .claim("type", EXTENSION_ACCESS)
                .claim("authVersion", authVersion(user))
                .claim("device", devicePublicId)
                .claim("tokenVersion", tokenVersion)
                .claim("scopes", scopes)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    public Claims parseAndRequireType(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get("type", String.class))) {
                throw new UnauthorizedException("Invalid token type");
            }
            return claims;
        } catch (UnauthorizedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    public String digest(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public long accessExpirationSeconds() {
        return properties.getAccessExpirationSeconds();
    }

    public long refreshExpirationSeconds() {
        return properties.getRefreshExpirationSeconds();
    }

    public long userId(Claims claims) {
        Object value = claims.get("uid");
        if (!(value instanceof Number number)) {
            throw new UnauthorizedException("Token user id is invalid");
        }
        return number.longValue();
    }

    public int userAuthVersion(Claims claims) {
        Object value = claims.get("authVersion");
        if (!(value instanceof Number number)) {
            throw new UnauthorizedException("Token auth version is invalid");
        }
        return number.intValue();
    }

    public String familyId(Claims claims) {
        String value = claims.get("family", String.class);
        if (value == null || value.isBlank()) {
            throw new UnauthorizedException("Token session family is invalid");
        }
        return value;
    }

    public String extensionDeviceId(Claims claims) {
        String value = claims.get("device", String.class);
        if (value == null || value.isBlank()) throw new UnauthorizedException("Extension device is invalid");
        return value;
    }

    public int extensionTokenVersion(Claims claims) {
        Object value = claims.get("tokenVersion");
        if (!(value instanceof Number number)) throw new UnauthorizedException("Extension token version is invalid");
        return number.intValue();
    }

    private String createToken(UserEntity user, String type, String tokenId, String familyId, long ttlSeconds) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(user.getPublicId())
                .id(tokenId)
                .claim("uid", user.getId())
                .claim("username", user.getUsername())
                .claim("type", type)
                .claim("authVersion", authVersion(user))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)));
        if (familyId != null) {
            builder.claim("family", familyId);
        }
        return builder.signWith(key).compact();
    }

    private int authVersion(UserEntity user) {
        return user.getAuthVersion() == null ? 0 : user.getAuthVersion();
    }
}
