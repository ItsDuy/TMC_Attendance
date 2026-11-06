package com.tmc.identity.api;

import com.tmc.identity.domain.QrCredential;
import com.tmc.identity.repo.QrCredentialRepository;
import com.tmc.shared.config.TmcProperties;
import com.tmc.shared.crypto.HmacUtil;
import com.tmc.shared.error.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class QrTokenService implements QrTokenPort {

    private static final String PREFIX = "TMC1";

    private final byte[] masterKey;
    private final Duration ttl;
    private final Duration clockSkew;
    private final QrCredentialRepository credentials;
    private final Clock clock;

    public QrTokenService(TmcProperties properties,
                          QrCredentialRepository credentials,
                          Clock clock) {
        this.masterKey = Base64.getDecoder().decode(properties.security().qrSecret());
        this.ttl = properties.security().qrTokenTtl();
        this.clockSkew = properties.security().kioskClockSkew();
        this.credentials = credentials;
        this.clock = clock;
    }

    @Override
    public String mint(UUID employeeId) {
        QrCredential credential = credentials.findById(employeeId)
                .orElseThrow(() -> new DomainException("QR_NOT_ISSUED", HttpStatus.NOT_FOUND,
                        "Employee has no QR credential"));

        long expiresAt = clock.instant().plus(ttl).getEpochSecond();
        String jti = UUID.randomUUID().toString();
        String payload = employeeId + "|" + jti + "|" + expiresAt + "|" + credential.getVersion();
        byte[] signature = sign(employeeId, payload);
        return PREFIX + "." + encode(payload) + "." + HmacUtil.base64Url(signature);
    }

    @Override
    public QrClaims verify(String token, Instant at) {
        if (token == null) {
            throw new InvalidQrTokenException(InvalidQrTokenException.Reason.MALFORMED);
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length != 3 || !PREFIX.equals(parts[0])) {
            throw new InvalidQrTokenException(InvalidQrTokenException.Reason.MALFORMED);
        }

        String payload;
        try {
            payload = new String(HmacUtil.base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidQrTokenException(InvalidQrTokenException.Reason.MALFORMED);
        }

        String[] fields = payload.split("\\|");
        if (fields.length != 4) {
            throw new InvalidQrTokenException(InvalidQrTokenException.Reason.MALFORMED);
        }

        UUID employeeId;
        long expiresAt;
        int version;
        try {
            employeeId = UUID.fromString(fields[0]);
            expiresAt = Long.parseLong(fields[2]);
            version = Integer.parseInt(fields[3]);
        } catch (RuntimeException e) {
            throw new InvalidQrTokenException(InvalidQrTokenException.Reason.MALFORMED);
        }

        byte[] presented;
        try {
            presented = HmacUtil.base64UrlDecode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new InvalidQrTokenException(InvalidQrTokenException.Reason.MALFORMED);
        }

        if (!HmacUtil.constantTimeEquals(sign(employeeId, payload), presented)) {
            throw new InvalidQrTokenException(InvalidQrTokenException.Reason.BAD_SIGNATURE);
        }
        if (!at.isBefore(Instant.ofEpochSecond(expiresAt))) {
            throw new InvalidQrTokenException(InvalidQrTokenException.Reason.EXPIRED);
        }
        // A token only exists from (expiry - ttl) onwards. Offline replays carry a
        // kiosk-asserted time, so without this lower bound an expired token could be
        // replayed by backdating the scan. Skew absorbs honest kiosk clock drift.
        Instant issuedAt = Instant.ofEpochSecond(expiresAt).minus(ttl);
        if (at.isBefore(issuedAt.minus(clockSkew))) {
            throw new InvalidQrTokenException(InvalidQrTokenException.Reason.NOT_YET_VALID);
        }

        return new QrClaims(employeeId, fields[1], Instant.ofEpochSecond(expiresAt), version);
    }

    @Override
    public String derivedKey(UUID employeeId) {
        return Base64.getEncoder().encodeToString(deriveKey(employeeId));
    }

    private byte[] sign(UUID employeeId, String payload) {
        return HmacUtil.hmacSha256(deriveKey(employeeId), payload);
    }

    private byte[] deriveKey(UUID employeeId) {
        return HmacUtil.hmacSha256(masterKey, "employee:" + employeeId);
    }

    private static String encode(String payload) {
        return HmacUtil.base64Url(payload.getBytes(StandardCharsets.UTF_8));
    }
}
