package com.ssafy.keeping.domain.auth.security;

import com.ssafy.keeping.domain.auth.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;

@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secret;

    private Key key;

//    private final long ACCESS_TOKEN_EXP = 1000L * 60 * 15;       // 15분
    private final long ACCESS_TOKEN_EXP =1000L * 60 * 60 * 24 * 7;;
    private final long REFRESH_TOKEN_EXP = 1000L * 60 * 60 * 24 * 7; // 7일

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    // access token 발급
    public String generateAccessToken(Long userId, UserRole role) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .claim("role", role.name())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXP))
                .signWith(key, SignatureAlgorithm.HS384)
                .compact();
    }

    // refresh token 발급
    public String generateRefreshToken(Long userId, UserRole role) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("role", role.name())
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXP))
                .signWith(key, SignatureAlgorithm.HS384)
                .compact();
    }

    // 토큰 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // userRole 꺼내기
    public UserRole getUserRole(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        String roleStr = claims.get("role", String.class);
        return roleStr != null ? UserRole.valueOf(roleStr) : null;

    }

    // userId 꺼내기
    public Long getUserId(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        return Long.valueOf(claims.getSubject());
    }

    // refreshToken 에서 userId 꺼내기
    public Long getUserIdFromRefresh(String token) {
        return getUserId(token);
    }

    // 토큰 만료시간 확인
    public Date getExpirationDate(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        return claims.getExpiration();
    }

    // 토큰 만료 확인
    public boolean isTokenExpired(String token) {
        try{
            Date expiration = getExpirationDate(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    // 생성시간 추출 메서드 추가
    public Date getIssuedAt(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
        return claims.getIssuedAt();
    }
}
