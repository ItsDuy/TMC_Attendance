package com.tmc.identity.api;

import com.tmc.identity.domain.QrCredential;
import com.tmc.identity.repo.QrCredentialRepository;
import com.tmc.shared.config.TmcProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QrTokenServiceTest {

    private static final Base64.Encoder B64 = Base64.getEncoder();

    private final UUID employeeId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-23T08:00:00Z");
    private QrTokenService service;

    @BeforeEach
    void setUp() {
        QrCredentialRepository credentials = mock(QrCredentialRepository.class);
        when(credentials.findById(employeeId))
                .thenReturn(Optional.of(new QrCredential(employeeId)));

        TmcProperties properties = new TmcProperties(
                new TmcProperties.Security(
                        B64.encodeToString("jwt-secret-that-is-long-enough-for-hs256".getBytes(StandardCharsets.UTF_8)),
                        Duration.ofHours(8),
                        B64.encodeToString("qr-secret-that-is-long-enough-for-hs256".getBytes(StandardCharsets.UTF_8)),
                        B64.encodeToString("data-key-that-is-long-enough-for-aes!!".getBytes(StandardCharsets.UTF_8)),
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(60)),
                new TmcProperties.Turnstile("stub", "http://localhost", Duration.ofSeconds(1)),
                new TmcProperties.Seed(false));

        service = new QrTokenService(properties, credentials,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void mintedTokenVerifiesInsideItsWindow() {
        String token = service.mint(employeeId);

        QrClaims claims = service.verify(token, now.plusSeconds(30));

        assertThat(claims.employeeId()).isEqualTo(employeeId);
        assertThat(claims.version()).isEqualTo(1);
        assertThat(claims.expiresAt()).isAfter(now.plusSeconds(30));
        assertThat(claims.jti()).isNotBlank();
    }

    @Test
    void tokenExpiresAfterItsTtl() {
        String token = service.mint(employeeId);

        assertThatThrownBy(() -> service.verify(token, now.plusSeconds(61)))
                .isInstanceOfSatisfying(InvalidQrTokenException.class,
                        ex -> assertThat(ex.reason())
                                .isEqualTo(InvalidQrTokenException.Reason.EXPIRED));
    }

    @Test
    void backdatedScanBeforeIssueTimeIsRejected() {
        String token = service.mint(employeeId);

        assertThatThrownBy(() -> service.verify(token, now.minusSeconds(120)))
                .isInstanceOfSatisfying(InvalidQrTokenException.class,
                        ex -> assertThat(ex.reason())
                                .isEqualTo(InvalidQrTokenException.Reason.NOT_YET_VALID));
    }

    @Test
    void honestKioskClockDriftIsTolerated() {
        String token = service.mint(employeeId);

        assertThat(service.verify(token, now.minusSeconds(30)).employeeId()).isEqualTo(employeeId);
    }

    @Test
    void tamperedSignatureIsRejected() {
        String token = service.mint(employeeId);
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + parts[2].substring(0, parts[2].length() - 2) + "AA";

        assertThatThrownBy(() -> service.verify(tampered, now))
                .isInstanceOfSatisfying(InvalidQrTokenException.class,
                        ex -> assertThat(ex.reason())
                                .isEqualTo(InvalidQrTokenException.Reason.BAD_SIGNATURE));
    }

    @Test
    void garbageIsRejectedAsMalformed() {
        assertThatThrownBy(() -> service.verify("not-a-token", now))
                .isInstanceOfSatisfying(InvalidQrTokenException.class,
                        ex -> assertThat(ex.reason())
                                .isEqualTo(InvalidQrTokenException.Reason.MALFORMED));
    }

    @Test
    void everyMintUsesAFreshNonce() {
        String first = service.mint(employeeId);
        String second = service.mint(employeeId);

        assertThat(first).isNotEqualTo(second);
        assertThat(service.verify(first, now).jti())
                .isNotEqualTo(service.verify(second, now).jti());
    }

    @Test
    void derivedKeyIsStablePerEmployee() {
        assertThat(service.derivedKey(employeeId))
                .isEqualTo(service.derivedKey(employeeId))
                .isNotEqualTo(service.derivedKey(UUID.randomUUID()));
    }
}
