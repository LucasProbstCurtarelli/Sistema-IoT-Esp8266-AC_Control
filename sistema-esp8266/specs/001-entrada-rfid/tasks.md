---

description: "Task list for entrada por chave RFID"
---

# Tasks: Entrada por chave RFID

**Input**: Design documents from `/specs/001-entrada-rfid/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Required by the constitution for the presence rule, the allowlist, and the lux threshold. JUnit 5 with doubles. One `sistema-esp8266/app/mvnw.cmd test` at the end. No Spring server, no firmware upload in this run.

**Organization**: Tasks are grouped by user story. The presence rule is one state machine; later stories extend the same service instead of a second orchestrator.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Firmware: `firmware/ProjetoAutomacaoResidencialLucas.ino`
- Orchestrator: `sistema-esp8266/app/src/main/java/com/iot/app/`
- Tests: `sistema-esp8266/app/src/test/java/com/iot/app/`
- Migration: `sistema-esp8266/app/src/main/resources/db/migration/`
- No frontend files in this feature

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the existing monorepo can take the delta without a new service

- [x] T001 Confirm root `.gitignore` already ignores Java build output (`target/`, `*.class`) and do not add a second runtime
- [x] T002 [P] Add the presence topic constant in `sistema-esp8266/app/src/main/java/com/iot/app/constants/ApplicationConstants.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Persistence, lamp port, absence timer, and MQTT subscribe that every story uses

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 Create Flyway migration `sistema-esp8266/app/src/main/resources/db/migration/V6__create_entrada_rfid.sql` with `chave_autorizada` and singleton `ajuste_entrada` after `V5__create_light_link_map.sql`
- [x] T004 [P] Create `AuthorizedKey` in `sistema-esp8266/app/src/main/java/com/iot/app/model/AuthorizedKey.java`
- [x] T005 [P] Create `EntranceSettings` in `sistema-esp8266/app/src/main/java/com/iot/app/model/EntranceSettings.java`
- [x] T006 [P] Create `AuthorizedKeyRepository` and `EntranceSettingsRepository` in `sistema-esp8266/app/src/main/java/com/iot/app/repository/`
- [x] T007 Create UID normalizer in `sistema-esp8266/app/src/main/java/com/iot/app/validation/EntranceUidNormalizer.java`
- [x] T008 Create `LampView` and `EntranceLampPort` in `sistema-esp8266/app/src/main/java/com/iot/app/service/entrance/EntranceLampPort.java`
- [x] T009 Create `AbsenceTimer`, `ScheduledAbsenceTimer` (5 seconds, arm once), and `EntranceSchedulingConfig` in `sistema-esp8266/app/src/main/java/com/iot/app/service/entrance/` and `sistema-esp8266/app/src/main/java/com/iot/app/config/EntranceSchedulingConfig.java`
- [x] T010 Add exact-topic subscribe plus resubscribe on reconnect in `sistema-esp8266/app/src/main/java/com/iot/app/service/MqttService.java` without a second broker client
- [x] T011 Cache the latest `tuya/+/state` payload in `sistema-esp8266/app/src/main/java/com/iot/app/service/TuyaLightingService.java` and expose `latestKnownState`

**Checkpoint**: Schema, ports, and MQTT subscribe exist. No presence decision yet.

---

## Phase 3: User Story 1 - Chegar em casa no escuro (Priority: P1) 🎯 MVP

**Goal**: A registered key in the dark turns both lamps on, or leaves a non-alert color alone. A bright arrival does nothing. The air conditioner is not commanded.

**Independent Test**: With the allowlist and threshold in place and lamps off, a registered UID and `lux` below the threshold asks both lamps to turn on. `lux` missing, threshold null, or `lux` greater than or equal to the threshold asks for no change. A failed lamp read still asks for lights on in the dark.

### Tests for User Story 1

- [x] T012 [P] [US1] Cover registered arrival, remembered color, already-on, bright, missing lux, null threshold, and failed read in `sistema-esp8266/app/src/test/java/com/iot/app/service/EntrancePresenceServiceTest.java`

### Implementation for User Story 1

- [x] T013 [US1] Implement registered arrival in `sistema-esp8266/app/src/main/java/com/iot/app/service/entrance/EntrancePresenceService.java` (read both lamps before the order; dark and `prefer_white` false and already on in a non-alert color sends nothing; otherwise white at 100 or `normal_color`)
- [x] T014 [US1] Implement `TuyaEntranceLampAdapter` in `sistema-esp8266/app/src/main/java/com/iot/app/service/entrance/TuyaEntranceLampAdapter.java` using cached `tuya/+/state` or a query, returning empty on failure, and commanding `lampada_1` and `lampada_2` through `TuyaLightingService`
- [x] T015 [US1] Persist manual non-alert color from `sistema-esp8266/app/src/main/java/com/iot/app/controller/LightingController.java` via `EntranceSettingsService` in `sistema-esp8266/app/src/main/java/com/iot/app/service/EntranceSettingsService.java`
- [x] T016 [US1] Refit `firmware/ProjetoAutomacaoResidencialLucas.ino`: IR send GPIO 5, IR recv GPIO 4, MFRC522 CLK 14 / MISO 12 / MOSI 13 / SS 2 / RST 16, LDR on A0, no RGB, Serial 115200, publish `/iot/entrada/presenca` on change and every 2 s, MQTT host/port/user/password from WiFiManager flash parameters, no `192.168.1.2` and no password literal, Coolix wait becomes a 600 ms deadline so the key scan keeps running

**Checkpoint**: Registered arrival is decidable without hardware. Firmware publishes facts and still accepts Coolix.

---

## Phase 4: User Story 2 - Sair de casa (Priority: P1)

**Goal**: The first `present: false` starts 5 seconds. Repeats, including the 2 s heartbeat, do not restart it. Only `present: true` cancels it. When the deadline fires, both lamps turn off.

**Independent Test**: Three `present: false` calls arm the timer once. Firing that single task turns both lamps off. The same UID returning before the fire cancels the task and does not turn the lamps off.

### Tests for User Story 2

- [x] T017 [P] [US2] Cover single-arm absence, heartbeat repeats, and same-UID cancel in `sistema-esp8266/app/src/test/java/com/iot/app/service/EntrancePresenceServiceTest.java`

### Implementation for User Story 2

- [x] T018 [US2] Arm `AbsenceTimer` only on the first empty reading and cancel it only from `present: true` in `sistema-esp8266/app/src/main/java/com/iot/app/service/entrance/EntrancePresenceService.java` and `sistema-esp8266/app/src/main/java/com/iot/app/service/entrance/ScheduledAbsenceTimer.java`

**Checkpoint**: Leaving the hook turns both lamps off after one 5 s deadline.

---

## Phase 5: User Story 3 - Chave desconhecida (Priority: P1)

**Goal**: An unknown key turns both lamps red at full brightness, day or night. When it leaves and the empty deadline completes, both lamps turn off and the next dark registered arrival uses white. A direct swap does not wait 5 s.

**Independent Test**: Unknown UID with or without `lux` commands `#FF0000` at brightness 100 on both lamps and never publishes `/iot/sensores/ac/comando`. Swap to a registered UID in the dark commands white; in the light it turns both off and sets `prefer_white`.

### Tests for User Story 3

- [x] T019 [P] [US3] Cover unknown alert, direct swaps, `prefer_white`, partial lamp failure, and no AC publish in `sistema-esp8266/app/src/test/java/com/iot/app/service/EntrancePresenceServiceTest.java`

### Implementation for User Story 3

- [x] T020 [US3] Implement unknown alert, direct UID swap, and `prefer_white` after an unknown departure in `sistema-esp8266/app/src/main/java/com/iot/app/service/entrance/EntrancePresenceService.java`

**Checkpoint**: Unknown and registered keys share one state machine. AC commands stay on the existing Coolix path.

---

## Phase 6: User Story 4 - Cadastrar uma chave (Priority: P2)

**Goal**: An authenticated admin adds or removes UIDs and sets the lux threshold under `/api/admin/entrada/`. Both survive a new service instance backed by the same repository. The ESP is not rewritten.

**Independent Test**: `04:a1:b2:c3` is stored as `04A1B2C3`. A repeat is rejected. A UID outside `^[0-9A-Fa-f]{8,20}$` is rejected. Threshold `350` remains after constructing the service again. `null` clears calibration. Values outside 0–1023 are rejected.

### Tests for User Story 4

- [x] T021 [P] [US4] Cover normalize, duplicate, reject, and recreate-from-repository in `sistema-esp8266/app/src/test/java/com/iot/app/service/AuthorizedKeyServiceTest.java` and `sistema-esp8266/app/src/test/java/com/iot/app/service/EntranceSettingsServiceTest.java`

### Implementation for User Story 4

- [x] T022 [US4] Implement allowlist add, remove, and lookup in `sistema-esp8266/app/src/main/java/com/iot/app/service/AuthorizedKeyService.java`
- [x] T023 [US4] Implement threshold and remembered color in `sistema-esp8266/app/src/main/java/com/iot/app/service/EntranceSettingsService.java`
- [x] T024 [US4] Implement `GET/POST/DELETE /api/admin/entrada/chaves` and `GET/PUT /api/admin/entrada/limiar` in `sistema-esp8266/app/src/main/java/com/iot/app/controller/EntranceAdminController.java` with DTOs in `sistema-esp8266/app/src/main/java/com/iot/app/dto/`, relying on the existing `/api/admin/**` `ROLE_ADMIN` rule in `sistema-esp8266/app/src/main/java/com/iot/app/security/SecurityConfig.java`

**Checkpoint**: Allowlist and threshold persist without a new screen and without a firmware change.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Wire the MQTT fact into the rule and check the sketch does not carry broker secrets

- [x] T025 Subscribe to `/iot/entrada/presenca` in `sistema-esp8266/app/src/main/java/com/iot/app/service/entrance/EntrancePresenceListener.java` and cover it in `sistema-esp8266/app/src/test/java/com/iot/app/service/EntrancePresenceListenerTest.java`
- [x] T026 Confirm `firmware/ProjetoAutomacaoResidencialLucas.ino` has no `192.168.1.2`, no password literal, no RGB pins, and does not publish lamp or AC command topics
- [x] T027 Run `.\mvnw.cmd test` once from `sistema-esp8266/app` and fix failures in the entrance tests before calling the feature done

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Setup. Blocks every user story
- **User Story 1 (Phase 3)**: Depends on Foundational. MVP
- **User Story 2 (Phase 4)**: Depends on the presence service from US1
- **User Story 3 (Phase 5)**: Depends on the same service and the absence timer from US2
- **User Story 4 (Phase 6)**: Depends on Foundational entities. Can be coded beside US1, and the presence service calls it
- **Polish (Phase 7)**: Depends on the stories above

### User Story Dependencies

- **User Story 1 (P1)**: First vertical slice of registered arrival
- **User Story 2 (P1)**: Adds the single 5 s deadline on that service
- **User Story 3 (P1)**: Adds unknown alert and swaps on that service
- **User Story 4 (P2)**: Admin API. No new frontend page

### Parallel Opportunities

- T004, T005, and T006 touch different files
- T012, T017, and T019 extend one test class and stay sequential
- T021 is a different test class from the presence tests
- Firmware T016 does not share files with the Java rule

---

## Parallel Example: User Story 4

```text
Task: "AuthorizedKeyServiceTest in sistema-esp8266/app/src/test/java/com/iot/app/service/AuthorizedKeyServiceTest.java"
Task: "EntranceSettingsServiceTest in sistema-esp8266/app/src/test/java/com/iot/app/service/EntranceSettingsServiceTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 and Phase 2
2. Complete Phase 3
3. Validate registered dark and bright arrival with doubles

### Incremental Delivery

1. Foundation: tables, ports, MQTT subscribe
2. US1: dark registered arrival and firmware facts
3. US2: one 5 s empty deadline
4. US3: red alert and direct swap
5. US4: admin allowlist and threshold
6. One Maven test run. Bench steps in `quickstart.md` stay on the hardware

### Parallel Team Strategy

Not used in this session. One implementer, one Maven test process.

---

## Notes

- First `present: false` arms 5 s. Another `present: false` does not restart it. Only `present: true` cancels it
- Lamp color is read from `tuya/+/state` or a query before deciding already-on or red. A failed read still turns lights on for a dark registered arrival
- Both `lampada_1` and `lampada_2` get the same effect. One failure does not skip the other. Both failing is not success; the next presence transition sends that order again
- Coolix and Tuya DPS 20/21/22/24 stay as they are. `tuyapi` stays out of Spring. AC is not part of the presence rule
- No Alexa, fauxmo, second ESP, Node orchestrator, or new frontend screen
