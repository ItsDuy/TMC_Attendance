package com.tmc.admin.seed;

import com.tmc.gate.api.KioskResponse;
import com.tmc.gate.api.KioskService;
import com.tmc.gate.domain.Gate;
import com.tmc.gate.domain.GateDirection;
import com.tmc.gate.repo.GateRepository;
import com.tmc.identity.api.CreateEmployeeRequest;
import com.tmc.identity.api.EmployeeResponse;
import com.tmc.identity.api.EmployeeService;
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
import java.util.List;

/**
 * Demo data for a fresh database. Disabled with {@code tmc.seed.enabled=false}.
 * The passwords and kiosk secrets printed here are for local demonstration only.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEMO_PASSWORD = "Password@123";

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
            return;
        }

        Factory binhDuong = factories.save(new Factory("BD", "Binh Duong Factory"));
        Factory longAn = factories.save(new Factory("LA", "Long An Factory"));

        List<KioskResponse> kioskList = new ArrayList<>();
        kioskList.addAll(provisionGateStation(binhDuong, "Gate A - In", GateDirection.IN));
        kioskList.addAll(provisionGateStation(binhDuong, "Gate A - Out", GateDirection.OUT));
        kioskList.addAll(provisionGateStation(binhDuong, "Gate B - Turnstile", GateDirection.BIDIRECTIONAL));
        kioskList.addAll(provisionGateStation(longAn, "Gate A - In", GateDirection.IN));
        kioskList.addAll(provisionGateStation(longAn, "Gate A - Out", GateDirection.OUT));

        List<EmployeeResponse> created = new ArrayList<>();
        created.addAll(createEmployees(binhDuong, "BD", List.of(
                "Nguyen Van An", "Tran Thi Binh", "Le Quang Cuong", "Pham Thi Dung",
                "Hoang Van Em", "Vu Thi Giang", "Do Van Hai", "Bui Thi Lan")));
        created.addAll(createEmployees(longAn, "LA", List.of(
                "Dang Van Minh", "Nguyen Thi Nga", "Truong Van Oanh", "Ly Thi Phuong")));

        AppUser admin = new AppUser("admin", passwordEncoder.encode(DEMO_PASSWORD), Role.ADMIN, null);
        AppUser hr = new AppUser("hr", passwordEncoder.encode(DEMO_PASSWORD), Role.HR, created.get(0).id());
        AppUser supervisor = new AppUser("supervisor", passwordEncoder.encode(DEMO_PASSWORD),
                Role.SUPERVISOR, created.get(1).id());
        AppUser guard = new AppUser("guard", passwordEncoder.encode(DEMO_PASSWORD), Role.SECURITY, null);
        AppUser executive = new AppUser("executive", passwordEncoder.encode(DEMO_PASSWORD),
                Role.EXECUTIVE, null);
        AppUser employee = new AppUser("employee", passwordEncoder.encode(DEMO_PASSWORD),
                Role.EMPLOYEE, created.get(0).id());
        users.saveAll(List.of(admin, hr, supervisor, guard, executive, employee));

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
                 Accounts (password {}):
                   admin / hr / supervisor / guard / executive / employee
                   employee account is linked to {}
                ----------------------------------------------------------------
                 Kiosks (id / name / secret):
                {}
                ================================================================
                """,
                DEMO_PASSWORD, created.get(0).employeeCode(), kioskTable);
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
