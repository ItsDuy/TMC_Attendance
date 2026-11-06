package com.tmc.attendance.core;

import com.tmc.identity.api.EmployeeView;
import com.tmc.identity.api.QrClaims;

public record VerifiedScan(QrClaims claims, EmployeeView employee) {
}
