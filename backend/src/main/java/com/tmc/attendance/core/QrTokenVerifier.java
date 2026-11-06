package com.tmc.attendance.core;

import com.tmc.identity.api.EmployeeLookup;
import com.tmc.identity.api.EmployeeView;
import com.tmc.identity.api.InvalidQrTokenException;
import com.tmc.identity.api.QrClaims;
import com.tmc.identity.api.QrTokenPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Turns a raw token into a trusted employee, or fails with a machine-readable
 * reason. Pure validation: no persistence and no gate actuation (SRP).
 */
@Component
public class QrTokenVerifier {

    private final QrTokenPort tokenPort;
    private final EmployeeLookup employees;

    public QrTokenVerifier(QrTokenPort tokenPort, EmployeeLookup employees) {
        this.tokenPort = tokenPort;
        this.employees = employees;
    }

    public VerifiedScan verify(ScanCommand command) {
        QrClaims claims;
        try {
            claims = tokenPort.verify(command.token(), command.scannedAt());
        } catch (InvalidQrTokenException e) {
            throw switch (e.reason()) {
                case EXPIRED -> new ScanDeniedException("QR_EXPIRED", ScanOutcome.EXPIRED,
                        "QR code has expired; refresh it in the app");
                case NOT_YET_VALID -> new ScanDeniedException("QR_NOT_YET_VALID", ScanOutcome.INVALID_TOKEN,
                        "Scan time is earlier than the QR code's issue time");
                case BAD_SIGNATURE -> new ScanDeniedException("QR_INVALID", ScanOutcome.INVALID_TOKEN,
                        "QR code signature is invalid");
                case MALFORMED -> new ScanDeniedException("QR_MALFORMED", ScanOutcome.INVALID_TOKEN,
                        "QR code is not a TMC token");
            };
        }

        Optional<EmployeeView> employee = employees.find(claims.employeeId());
        if (employee.isEmpty()) {
            throw new ScanDeniedException("EMPLOYEE_UNKNOWN", ScanOutcome.DENIED,
                    "Employee record does not exist");
        }
        if (claims.version() != employee.get().credentialVersion()) {
            throw new ScanDeniedException("QR_REVOKED", ScanOutcome.DENIED,
                    "QR code was rotated or revoked");
        }
        return new VerifiedScan(claims, employee.get());
    }
}
