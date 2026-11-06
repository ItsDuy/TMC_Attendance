package com.tmc.admin.seed;

import com.tmc.gate.api.KioskResponse;
import com.tmc.gate.api.KioskService;
import com.tmc.gate.domain.Gate;
import com.tmc.gate.domain.GateDirection;
import com.tmc.gate.repo.GateRepository;
import com.tmc.identity.api.CreateEmployeeRequest;
import com.tmc.identity.api.EmployeeResponse;
import com.tmc.identity.api.EmployeeService;
import com.tmc.identity.api.EmployeeView;
import com.tmc.identity.domain.Factory;
import com.tmc.identity.repo.FactoryRepository;
import com.tmc.security.api.Role;
import com.tmc.security.domain.AppUser;
import com.tmc.security.repo.AppUserRepository;
import com.tmc.shared.config.TmcProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Demo data for a fresh database. Disabled with {@code tmc.seed.enabled=false}.
 * The passwords and kiosk secrets printed here are for local demonstration only.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEMO_PASSWORD = "Password@123";
    private static final Pattern SEEDED_CODE = Pattern.compile("(BD|LA)\\d{3}");

    private final TmcProperties properties;
    private final FactoryRepository factories;
    private final GateRepository gates;
    private final EmployeeService employees;
    private final KioskService kiosks;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(TmcProperties properties, FactoryRepository factories, GateRepository gates,
                      EmployeeService employees, KioskService kiosks, AppUserRepository users,
                      PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.factories = factories;
        this.gates = gates;
        this.employees = employees;
        this.kiosks = kiosks;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.seed().enabled()) {
            return;
        }
        if (factories.count() > 0) {
            log.info("Seed skipped: factories already present");
        } else {
            seedFreshDatabase();
        }
        ensureDemoAccounts();
    }

    private void seedFreshDatabase() {

        Factory binhDuong = factories.save(new Factory("BD", "Binh Duong Factory"));
        Factory longAn = factories.save(new Factory("LA", "Long An Factory"));

        List<KioskResponse> kioskList = new ArrayList<>();
        kioskList.addAll(provisionGateStation(binhDuong, "Gate A - In", GateDirection.IN));
        kioskList.addAll(provisionGateStation(binhDuong, "Gate A - Out", GateDirection.OUT));
        kioskList.addAll(provisionGateStation(binhDuong, "Gate B - Turnstile", GateDirection.BIDIRECTIONAL));
        kioskList.addAll(provisionGateStation(longAn, "Gate A - In", GateDirection.IN));
        kioskList.addAll(provisionGateStation(longAn, "Gate A - Out", GateDirection.OUT));

        createEmployees(binhDuong, "BD", List.of(
                "Nguyen Van An", "Tran Thi Binh", "Le Quang Cuong", "Pham Thi Dung",
                "Hoang Van Em", "Vu Thi Giang", "Do Van Hai", "Bui Thi Lan"));
        createEmployees(longAn, "LA", List.of(
                "Dang Van Minh", "Nguyen Thi Nga", "Truong Van Oanh", "Ly Thi Phuong"));


        StringBuilder kioskTable = new StringBuilder();
        for (KioskResponse kiosk : kioskList) {
            kioskTable.append("                   ")
                    .append(kiosk.id()).append("  ")
                    .append(kiosk.name()).append("  secret=")
                    .append(kiosk.secret()).append('\n');
        }

        log.info("""
                
                ================================================================
                 TMC demo data seeded
                ================================================================
                 Accounts: see the "Demo accounts" block below
                ----------------------------------------------------------------
                 Kiosks (id / name / secret):
                {}
                ================================================================
                """,
                kioskTable);
    }

    /**
     * Idempotent, runs on every start: one login per seeded employee (username = employee
     * code in lower case) plus the staff accounts, each staff account linked to a DIFFERENT
     * employee so nobody shares an attendance history by accident.
     */
    private void ensureDemoAccounts() {
        Map<String, EmployeeView> byCode = new HashMap<>();
        for (EmployeeView employee : employees.findAll()) {
            if (SEEDED_CODE.matcher(employee.employeeCode()).matches()) {
                byCode.put(employee.employeeCode(), employee);
            }
        }
        if (byCode.isEmpty()) {
            return;
        }

        List<String> added = new ArrayList<>();
        ensure("admin", Role.ADMIN, null, added);
        ensure("guard", Role.SECURITY, null, added);
        ensure("executive", Role.EXECUTIVE, null, added);
        ensure("employee", Role.EMPLOYEE, idOf(byCode, "BD001"), added);
        ensure("supervisor", Role.SUPERVISOR, idOf(byCode, "BD002"), added);
        ensure("hr", Role.HR, idOf(byCode, "BD003"), added);
        byCode.keySet().stream().sorted().forEach(code ->
                ensure(code.toLowerCase(), Role.EMPLOYEE, byCode.get(code).id(), added));

        if (!added.isEmpty()) {
            log.info("Demo accounts added or re-linked: {}", String.join(", ", added));
        }
        log.info("""

                ================================================================
                 Demo accounts (password {})
                ----------------------------------------------------------------
                 admin       System administrator
                 hr          HR staff               = BD003 {}
                 supervisor  Production supervisor  = BD002 {}
                 guard       Security guard
                 executive   Executive management
                 employee    Employee               = BD001 {}
                 bd001 … bd008, la001 … la004   one login per employee
                ================================================================
                """,
                DEMO_PASSWORD, nameOf(byCode, "BD003"), nameOf(byCode, "BD002"), nameOf(byCode, "BD001"));
    }

    /** Create the account if missing; re-link a seeded account that points at the wrong person. */
    private void ensure(String username, Role role, UUID employeeId, List<String> added) {
        AppUser existing = users.findByUsername(username).orElse(null);
        if (existing == null) {
            users.save(new AppUser(username, passwordEncoder.encode(DEMO_PASSWORD), role, employeeId));
            added.add(username);
        } else if (employeeId != null && !employeeId.equals(existing.getEmployeeId())) {
            existing.linkEmployee(employeeId);
            users.save(existing);
            added.add(username + " (re-linked)");
        }
    }

    private static UUID idOf(Map<String, EmployeeView> byCode, String code) {
        EmployeeView employee = byCode.get(code);
        return employee == null ? null : employee.id();
    }

    private static String nameOf(Map<String, EmployeeView> byCode, String code) {
        EmployeeView employee = byCode.get(code);
        return employee == null ? "" : employee.fullName();
    }

    private List<KioskResponse> provisionGateStation(Factory factory, String gateName,
                                                     GateDirection direction) {
        Gate gate = gates.save(new Gate(factory.getId(), gateName, direction));
        KioskResponse kiosk = kiosks.create(gate.getId(), gateName + " Kiosk", "seed");
        return List.of(kiosk);
    }

    private List<EmployeeResponse> createEmployees(Factory factory, String prefix, List<String> names) {
        List<EmployeeResponse> created = new ArrayList<>();
        for (int index = 0; index < names.size(); index++) {
            String number = String.format("%03d", index + 1);
            String email = prefix.toLowerCase() + ".employee" + number + "@tmc.example";
            created.add(employees.create(new CreateEmployeeRequest(
                    prefix + number,
                    names.get(index),
                    factory.getId(),
                    index % 3 == 0 ? "Assembly" : index % 3 == 1 ? "Quality" : "Logistics",
                    email,
                    "+8490000" + number), "seed"));
        }
        return created;
    }
}
