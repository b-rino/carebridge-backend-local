# 2FA Implementation – Carebridge Backend

Komplet oversigt over alt hvad der er implementeret i forbindelse med TOTP-baseret 2-faktor autentificering.

---

## Indholdsfortegnelse

1. [Overblik og flow](#overblik-og-flow)
2. [Database – nye kolonner](#database--nye-kolonner)
3. [Backend – nye og ændrede filer](#backend--nye-og-ændrede-filer)
4. [API-endpoints](#api-endpoints)
5. [Token-strategi](#token-strategi)
6. [Bugfixes opdaget undervejs](#bugfixes-opdaget-undervejs)
7. [Tests](#tests)
8. [Dokumentation](#dokumentation)

---

## Overblik og flow

2FA er implementeret med TOTP (Time-based One-Time Password) via Google Authenticator eller tilsvarende app. Flowet er opdelt i to scenarier:

### Flow 1 – Første login (ingen 2FA opsat)
```
Bruger → POST /auth/login
       ← { requiresTotpSetup: true, tempToken (scope: SETUP, 5 min) }

Bruger → GET /auth/2fa/setup  [Authorization: Bearer <tempToken>]
       ← { secret, otpauthUri }

Bruger scanner QR-kode i Google Authenticator

Bruger → POST /auth/2fa/confirm  [Authorization: Bearer <tempToken>]
          Body: { code: "123456" }
       ← { token (fuld JWT, 14 dage) }
```

### Flow 2 – Efterfølgende login (2FA konfigureret)
```
Bruger → POST /auth/login
       ← { requires2FA: true, tempToken (scope: VERIFY, 5 min) }

Bruger → POST /auth/2fa/verify  [Authorization: Bearer <tempToken>]
          Body: { code: "123456" }
       ← { token (fuld JWT, 14 dage) }
```

---

## Database – nye kolonner

Tilføjet på `users`-tabellen via JPA/Hibernate:

| Kolonne | Type | Default | Beskrivelse |
|---------|------|---------|-------------|
| `totp_secret` | VARCHAR | NULL | Base32-kodet TOTP-secret genereret ved opsætning |
| `totp_enabled` | BOOLEAN | false | Sættes til `true` når brugeren har bekræftet opsætningen |

> **Vigtigt:** Login-flowet tjekker `totp_enabled` (ikke blot om `totp_secret` er sat) for at afgøre om brugeren skal til setup eller verify. Dette er en bevidst beslutning for at håndtere afbrudte opsætninger korrekt (se bugfixes).

---

## Backend – nye og ændrede filer

### `User.java` – Entity
**Tilføjet:**
- `@Column(name = "totp_secret") private String totpSecret`
- `@Column(name = "totp_enabled", nullable = false) private boolean totpEnabled = false`
- Getters og setters for begge felter

---

### `TotpService.java` – Ny service
Wrapper omkring `dev.samstevens.totp`-biblioteket.

| Metode | Beskrivelse |
|--------|-------------|
| `generateSecret()` | Genererer et nyt base32 TOTP-secret |
| `getOtpAuthUri(secret, email)` | Bygger `otpauth://`-URI til QR-kode |
| `verifyCode(secret, code)` | Validerer en 6-cifret TOTP-kode mod secret |
| `generateCurrentCode(secret)` | Genererer den aktuelle gyldige kode (bruges i tests) |

---

### `TokenSecurity.java` – Udvidet
**Tilføjet to nye metoder til temp tokens:**

| Metode | Beskrivelse |
|--------|-------------|
| `createTempToken(email, preAuthType, issuer, secret)` | Opretter et kortlivet JWT (5 min) med `preAuthType`-claim (`SETUP` eller `VERIFY`) |
| `validateTempToken(token, expectedPreAuthType, secret)` | Validerer signatur, udløb og at typen matcher — returnerer email |

**Sikkerhedsbeskyttelse:** `getUserWithRolesFromToken()` afviser eksplicit temp tokens ved at tjekke om `preAuthType`-claim er til stede. Temp tokens kan aldrig bruges til almindelig autentificering.

---

### `ITokenSecurity.java` – Interface udvidet
Tilføjet signaturer for `createTempToken()` og `validateTempToken()`.

---

### `SecurityDAO.java` – Udvidet
**Tilføjet metoder:**

| Metode | Beskrivelse |
|--------|-------------|
| `saveTotpSecret(email, secret)` | Gemmer secret og sætter `totp_enabled = false` |
| `enableTotp(email)` | Sætter `totp_enabled = true` efter bekræftet kode |
| `getUserByEmail(email)` | Henter bruger til brug i TOTP-verify |

---

### `ISecurityDAO.java` – Interface udvidet
Tilføjet signaturer for de tre nye DAO-metoder.

---

### `SecurityController.java` – Kernelogik
**`login()` ændret:**
- Tidligere: returnerede fuld JWT direkte
- Nu: tjekker `isTotpEnabled()` og returnerer enten `requiresTotpSetup` eller `requires2FA` + temp token

**Nye handlers tilføjet:**

| Handler | HTTP | Endpoint | Beskrivelse |
|---------|------|----------|-------------|
| `totpSetup()` | GET | `/auth/2fa/setup` | Genererer secret, gemmer i DB, returnerer QR-URI |
| `totpConfirm()` | POST | `/auth/2fa/confirm` | Validerer første kode, aktiverer 2FA, returnerer fuld JWT |
| `totpVerify()` | POST | `/auth/2fa/verify` | Validerer TOTP-kode ved login, returnerer fuld JWT |

Alle tre handlers validerer temp token via `extractEmailFromTempToken()` med scope-tjek.

---

### `SecurityRoutes.java` – Udvidet
Tilføjet routes:
```java
path("/2fa", () -> {
    get("/setup",   security.totpSetup(),   Role.ANYONE);
    post("/confirm", security.totpConfirm(), Role.ANYONE);
    post("/verify",  security.totpVerify(),  Role.ANYONE);
});
```

---

### `TotpCodeRequest.java` – Ny DTO
Simpel DTO til at modtage `{ "code": "123456" }` i request body.

---

## API-endpoints

| Method | Endpoint | Auth | Beskrivelse |
|--------|----------|------|-------------|
| `POST` | `/api/auth/login` | Ingen | Returnerer temp token (SETUP eller VERIFY) |
| `GET` | `/api/auth/2fa/setup` | Bearer `<SETUP-token>` | Returnerer TOTP-secret og QR-URI |
| `POST` | `/api/auth/2fa/confirm` | Bearer `<SETUP-token>` | Bekræfter opsætning, returnerer fuld JWT |
| `POST` | `/api/auth/2fa/verify` | Bearer `<VERIFY-token>` | Verificerer login-kode, returnerer fuld JWT |

---

## Token-strategi

Systemet opererer med to typer tokens:

| Type | Levetid | Claim | Formål |
|------|---------|-------|--------|
| **Temp token** | 5 minutter | `preAuthType: "SETUP"` eller `"VERIFY"` | Bruges udelukkende til 2FA-flowet |
| **Fuld JWT** | 14 dage | Roller, username | Bruges til al øvrig autentificeret adgang |

Temp tokens kan **ikke** bruges til at tilgå beskyttede endpoints — `getUserWithRolesFromToken()` afviser dem eksplicit.

---

## Bugfixes opdaget undervejs

### Bug 1 – Afbrudt opsætning omdirigerede til verify-siden
**Problem:** Login tjekkede `totpSecret == null` for at afgøre om brugeren skulle til setup. Hvis en bruger påbegyndte setup (secret gemt i DB), men aldrig confirmede, ville næste login sende dem til verify-siden — uden at have en QR-kode at scanne.

**Fix:** Login tjekker nu `!isTotpEnabled()` i stedet for `totpSecret == null`.

```java
// Før (bug)
if (verified.getTotpSecret() == null) { ... }

// Efter (fix)
if (!verified.isTotpEnabled()) { ... }
```

---

## Tests

### `SecurityTest.java` – Ny testklasse (10 tests)
Bruger RestAssured mod en Javalin-testserver på port 7071 med Testcontainers PostgreSQL.

| Test | Order | Dækker |
|------|-------|--------|
| `AC1_loginUden2FA_returnsTotpSetupRequired` | 1 | Login uden 2FA → `requiresTotpSetup: true`, `tempToken` sat, ingen `token` |
| `AC2_setupTempToken_kanIkkeTilgaaBeskyttetEndpoint` | 2 | SETUP-token giver 401 på `/events` |
| `totpSetup_returnerSecretOgQrUri` | 3 | GET /2fa/setup returnerer `secret` og `otpauthUri` |
| `AC3_totpConfirm_medForkertKode_returns401` | 4 | Ugyldig kode → 401 med fejlbesked |
| `totpConfirm_medKorrektKode_returnerFuldToken` | 5 | Korrekt kode → fuld JWT i response |
| `afbrydtOpsaetning_loginSenderTilSetup_ikkeVerify` | 6 | Bruger med `totp_secret` men `totp_enabled=false` → `requiresTotpSetup` (bugfix-test) |
| `AC4_loginMed2FA_returnerVerifyRequired` | 7 | Login med 2FA → `requires2FA: true`, `tempToken` sat, ingen `token` |
| `AC4_totpVerify_medForkertKode_returns401` | 8 | Forkert kode ved verify → 401 |
| `AC4_totpVerify_medKorrektKode_returnerFuldToken` | 9 | Korrekt kode → fuld JWT |
| `fuldToken_kanTilgaaBeskyttetEndpoint` | 10 | Fuld JWT giver adgang til `/events` |

### Testbrugere i `Populator.java`

| Bruger | `totp_enabled` | `totp_secret` | Formål |
|--------|---------------|---------------|--------|
| `admin@carebridge.io` | `true` | `ADMIN_TOTP_SECRET` | Admin-bruger med fuld 2FA |
| `alice@carebridge.io` | `true` | `ALICE_TOTP_SECRET` | Careworker-bruger med fuld 2FA |
| `no2fa@carebridge.io` | `false` | `null` | Bruger der aldrig har sat 2FA op |
| `partial@carebridge.io` | `false` | `PARTIAL_TOTP_SECRET` | Bruger der afbrød opsætningen |

TOTP-secrets er eksponerede som `public static final`-konstanter på `Populator` så tests kan generere gyldige koder via `TotpService.generateCurrentCode(secret)`.

### Reparerede eksisterende tests
- **`EventTest.java`** og **`EventTypeTest.java`**: Opdateret `@BeforeAll` til at gennemføre det fulde 2FA login-flow (login → verify) for at hente tokens. Tilføjet `@AfterAll` der stopper Javalin-serveren.
- **`EventTest.java`**: Fikset pre-eksisterende bug hvor `testReadEventById` og `testUpdateEvent` hardkodede `/events/1` i stedet for at bruge det dynamisk oprettede event-ID.
- **`EventDAO.java`**: Fikset pre-eksisterende `LazyInitializationException` på `Event.seenByUsers` i `read()` og `update()` ved at erstatte `em.find()` med JPQL `LEFT JOIN FETCH`.

---

## Dokumentation

| Fil | Indhold |
|-----|---------|
| `USER_STORY_2FA.md` | User story med 11 acceptkriterier (AC1–AC11) på dansk |
| `2fa_robustness.puml` | PlantUML Robustness Diagram over AC1–AC5 med user story tekst til venstre |
| `2FA_IMPLEMENTATION.md` | Denne fil |
