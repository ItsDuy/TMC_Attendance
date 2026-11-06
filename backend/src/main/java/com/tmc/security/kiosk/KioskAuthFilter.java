package com.tmc.security.kiosk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmc.security.api.KioskAuthenticator;
import com.tmc.shared.config.TmcProperties;
import com.tmc.shared.crypto.HmacUtil;
import com.tmc.shared.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Per-kiosk HMAC authentication (ADR-017). Every kiosk request carries an id, a
 * timestamp and a signature over the exact body, so it cannot be replayed
 * outside the clock-skew window or tampered with in transit.
 */
@Component
public class KioskAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_ID = "X-Kiosk-Id";
    private static final String HEADER_TIMESTAMP = "X-Kiosk-Timestamp";
    private static final String HEADER_SIGNATURE = "X-Kiosk-Signature";

    private final KioskAuthenticator authenticator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration allowedSkew;

    public KioskAuthFilter(KioskAuthenticator authenticator, ObjectMapper objectMapper,
                           Clock clock, TmcProperties properties) {
        this.authenticator = authenticator;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.allowedSkew = properties.security().kioskClockSkew();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/kiosk/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CachedBodyRequest wrapped = new CachedBodyRequest(request);

        String kioskId = request.getHeader(HEADER_ID);
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String signature = request.getHeader(HEADER_SIGNATURE);

        if (isBlank(kioskId) || isBlank(timestamp) || isBlank(signature)) {
            reject(response, request, "KIOSK_UNAUTHENTICATED", "Missing kiosk authentication headers");
            return;
        }

        long epochMillis;
        try {
            epochMillis = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            reject(response, request, "KIOSK_BAD_TIMESTAMP", "Timestamp is not a number");
            return;
        }

        Instant presented = Instant.ofEpochMilli(epochMillis);
        if (Duration.between(presented, clock.instant()).abs().compareTo(allowedSkew) > 0) {
            reject(response, request, "KIOSK_CLOCK_SKEW", "Kiosk timestamp outside the allowed window");
            return;
        }

        var credentials = authenticator.authenticate(kioskId).orElse(null);
        if (credentials == null || !credentials.active()) {
            reject(response, request, "KIOSK_UNKNOWN", "Unknown or disabled kiosk");
            return;
        }

        String canonical = kioskId + "\n" + timestamp + "\n" + wrapped.bodyAsString();
        String expected = HmacUtil.hex(HmacUtil.hmacSha256(
                credentials.secret().getBytes(StandardCharsets.UTF_8), canonical));

        if (!HmacUtil.constantTimeEquals(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            reject(response, request, "KIOSK_BAD_SIGNATURE", "Kiosk signature does not match");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                new KioskPrincipal(credentials.kioskId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_KIOSK")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(wrapped, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void reject(HttpServletResponse response, HttpServletRequest request,
                        String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(401, code, message, request.getRequestURI()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
