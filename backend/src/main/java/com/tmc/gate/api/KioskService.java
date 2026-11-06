package com.tmc.gate.api;

import com.tmc.gate.domain.Gate;
import com.tmc.gate.domain.Kiosk;
import com.tmc.gate.domain.KioskStatus;
import com.tmc.gate.repo.GateRepository;
import com.tmc.gate.repo.KioskRepository;
import com.tmc.identity.api.EmployeeLookup;
import com.tmc.identity.api.EmployeeView;
import com.tmc.identity.api.QrTokenPort;
import com.tmc.identity.domain.CredentialStatus;
import com.tmc.identity.domain.EmployeeStatus;
import com.tmc.security.api.AuditPort;
import com.tmc.security.api.KioskAuthenticator;
import com.tmc.shared.config.TmcProperties;
import com.tmc.shared.crypto.AesGcmCipher;
import com.tmc.shared.error.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class KioskService implements KioskLookup, KioskAuthenticator, GateStatusLookup {

    private final KioskRepository kiosks;
    private final GateRepository gates;
    private final EmployeeLookup employeeLookup;
    private final QrTokenPort qrTokenPort;
    private final AesGcmCipher cipher;
    private final AuditPort audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final long qrTtlSeconds;

    public KioskService(KioskRepository kiosks,
                        GateRepository gates,
                        EmployeeLookup employeeLookup,
                        QrTokenPort qrTokenPort,
                        AesGcmCipher cipher,
                        AuditPort audit,
                        Clock clock,
                        TmcProperties properties) {
        this.kiosks = kiosks;
        this.gates = gates;
        this.employeeLookup = employeeLookup;
        this.qrTokenPort = qrTokenPort;
        this.cipher = cipher;
        this.audit = audit;
        this.clock = clock;
        this.qrTtlSeconds = properties.security().qrTokenTtl().toSeconds();
    }

    @Transactional
    public KioskResponse create(UUID gateId, String name, String actor) {
        Gate gate = gates.findById(gateId)
                .orElseThrow(() -> DomainException.notFound("Gate", gateId));
        String secret = newSecret();
        Kiosk kiosk = new Kiosk(gate.getId(), gate.getFactoryId(), name, cipher.encrypt(secret));
        kiosks.save(kiosk);

        audit.record(actor, "KIOSK_CREATED", "Kiosk", kiosk.getId().toString(),
                Map.of("gateId", gateId.toString(), "name", name));

        return toResponse(kiosk, secret);
    }

    @Transactional(readOnly = true)
    public List<KioskResponse> list(UUID factoryId) {
        List<Kiosk> found = factoryId == null
                ? kiosks.findAll()
                : kiosks.findByFactoryIdOrderByNameAsc(factoryId);
        return found.stream().map(kiosk -> toResponse(kiosk, null)).toList();
    }

    @Transactional
    public KioskResponse rotateSecret(UUID kioskId, String actor) {
        Kiosk kiosk = kiosks.findById(kioskId)
                .orElseThrow(() -> DomainException.notFound("Kiosk", kioskId));
        String secret = newSecret();
        kiosk.rotateSecret(cipher.encrypt(secret));
        audit.record(actor, "KIOSK_SECRET_ROTATED", "Kiosk", kioskId.toString(),
                Map.of("configVersion", kiosk.getConfigVersion()));
        return toResponse(kiosk, secret);
    }

    @Transactional
    public void heartbeat(UUID kioskId) {
        Kiosk kiosk = kiosks.findById(kioskId)
                .orElseThrow(() -> DomainException.notFound("Kiosk", kioskId));
        kiosk.heartbeat(clock.instant());
    }

    @Transactional(readOnly = true)
    public ProvisioningBundle provision(UUID kioskId) {
        Kiosk kiosk = kiosks.findById(kioskId)
                .orElseThrow(() -> DomainException.notFound("Kiosk", kioskId));
        Gate gate = gates.findById(kiosk.getGateId())
                .orElseThrow(() -> DomainException.notFound("Gate", kiosk.getGateId()));

        List<ProvisioningBundle.EmployeeKey> keys = employeeLookup
                .findByFactory(kiosk.getFactoryId()).stream()
                .filter(employee -> employee.status() == EmployeeStatus.ACTIVE)
                .filter(employee -> employee.credentialStatus() == CredentialStatus.ACTIVE)
                .map(employee -> new ProvisioningBundle.EmployeeKey(
                        employee.id(), employee.employeeCode(), employee.fullName(),
                        employee.credentialVersion(), qrTokenPort.derivedKey(employee.id())))
                .toList();

        return new ProvisioningBundle("TMC1", qrTtlSeconds, kiosk.getId(), kiosk.getFactoryId(),
                gate.getId(), gate.getDirection().name(), kiosk.getConfigVersion(), keys);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GateStatusView> gateStatuses() {
        Map<UUID, Instant> lastBeat = new HashMap<>();
        for (Kiosk kiosk : kiosks.findAll()) {
            if (kiosk.getLastHeartbeatAt() != null) {
                lastBeat.merge(kiosk.getGateId(), kiosk.getLastHeartbeatAt(),
                        (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        return gates.findAll().stream()
                .map(gate -> new GateStatusView(gate.getId(), gate.getFactoryId(), gate.getName(),
                        gate.getDirection(), lastBeat.get(gate.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KioskView> find(UUID kioskId) {
        return kiosks.findById(kioskId).flatMap(kiosk ->
                gates.findById(kiosk.getGateId()).map(gate -> new KioskView(
                        kiosk.getId(), gate.getId(), kiosk.getFactoryId(), kiosk.getName(),
                        gate.getDirection(), kiosk.getStatus())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KioskCredentials> authenticate(String kioskId) {
        UUID id;
        try {
            id = UUID.fromString(kioskId);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        return kiosks.findById(id)
                .map(kiosk -> new KioskCredentials(kiosk.getId(), cipher.decrypt(kiosk.getSecretEnc()),
                        kiosk.getStatus() == KioskStatus.ACTIVE));
    }

    private String newSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static KioskResponse toResponse(Kiosk kiosk, String plaintextSecret) {
        return new KioskResponse(kiosk.getId(), kiosk.getGateId(), kiosk.getFactoryId(),
                kiosk.getName(), kiosk.getStatus(), kiosk.getConfigVersion(),
                kiosk.getLastHeartbeatAt(), plaintextSecret);
    }
}
