package restTest;

import com.carebridge.config.ApplicationConfig;
import com.carebridge.config.HibernateConfig;
import com.carebridge.config.Populator;
import com.carebridge.services.TotpService;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.restassured.RestAssured;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SecurityTest {

    private Javalin app;
    private final TotpService totp = new TotpService();

    // State delt på tværs af tests i Flow 1 (setup-flow)
    private String setupTempToken;
    private String discoveredSecret;

    // State delt på tværs af tests i Flow 2 (verify-flow)
    private String verifyTempToken;
    private String fullToken;

    @BeforeAll
    public void setup() {
        HibernateConfig.setTest(true);
        app = ApplicationConfig.startServer(7071);
        Populator.populate(HibernateConfig.getEntityManagerFactoryForTest());
        RestAssured.baseURI = "http://localhost:7071/api";
    }

    @AfterAll
    public void teardown() {
        ApplicationConfig.stopServer(app);
    }

    // ═══════════════════════════════════════════════════
    // FLOW 1 – Første login: tvungen 2FA-opsætning
    // AC1: ingen 2FA → requiresTotpSetup
    // ═══════════════════════════════════════════════════

    @Test
    @Order(1)
    void AC1_loginUden2FA_returnsSetupRequired() {
        setupTempToken = given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"no2fa@carebridge.io\", \"password\":\"password123\"}")
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("requiresTotpSetup", equalTo(true))
                .body("tempToken", notNullValue())
                .body("token", nullValue())
                .extract().path("tempToken");
    }

    // AC2: SETUP-token giver ikke adgang til beskyttede endpoints
    @Test
    @Order(2)
    void AC2_setupTempToken_kanIkkeTilgaaBeskyttetEndpoint() {
        given()
                .header("Authorization", "Bearer " + setupTempToken)
                .get("/events")
                .then()
                .statusCode(401);
    }

    // Setup-flow: hent QR-kode og secret
    @Test
    @Order(3)
    void totpSetup_returnerSecretOgQrUri() {
        discoveredSecret = given()
                .header("Authorization", "Bearer " + setupTempToken)
                .get("/auth/2fa/setup")
                .then()
                .statusCode(200)
                .body("secret", notNullValue())
                .body("otpauthUri", notNullValue())
                .extract().path("secret");
    }

    // AC3: ugyldig kode under opsætning → 401 med fejlbesked
    @Test
    @Order(4)
    void AC3_totpConfirm_medForkertKode_returns401() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + setupTempToken)
                .body("{\"code\":\"000000\"}")
                .post("/auth/2fa/confirm")
                .then()
                .statusCode(401)
                .body("msg", notNullValue());
    }

    // Setup-flow: bekræft med korrekt kode → fuld token
    @Test
    @Order(5)
    void totpConfirm_medKorrektKode_returnerFuldToken() throws Exception {
        String validCode = totp.generateCurrentCode(discoveredSecret);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + setupTempToken)
                .body("{\"code\":\"" + validCode + "\"}")
                .post("/auth/2fa/confirm")
                .then()
                .statusCode(200)
                .body("token", notNullValue());
    }

    // ═══════════════════════════════════════════════════
    // FLOW 1B – Afbrudt opsætning (bugfix)
    // Bruger har totp_secret men totp_enabled=false
    // → skal sendes til setup-flow, IKKE verify-flow
    // ═══════════════════════════════════════════════════

    @Test
    @Order(6)
    void afbrydtOpsaetning_loginSenderTilSetup_ikkeVerify() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"partial@carebridge.io\", \"password\":\"password123\"}")
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("requiresTotpSetup", equalTo(true))
                .body("requires2FA", nullValue());
    }

    // ═══════════════════════════════════════════════════
    // FLOW 2 – Login med eksisterende 2FA
    // AC4: 2FA påkrævet ved login
    // ═══════════════════════════════════════════════════

    @Test
    @Order(7)
    void AC4_loginMed2FA_returnerVerifyRequired() {
        verifyTempToken = given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"alice@carebridge.io\", \"password\":\"password123\"}")
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("requires2FA", equalTo(true))
                .body("tempToken", notNullValue())
                .body("token", nullValue())
                .extract().path("tempToken");
    }

    // AC4: forkert 2FA-kode → 401 med fejlbesked
    @Test
    @Order(8)
    void AC4_totpVerify_medForkertKode_returns401() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + verifyTempToken)
                .body("{\"code\":\"000000\"}")
                .post("/auth/2fa/verify")
                .then()
                .statusCode(401)
                .body("msg", notNullValue());
    }

    // AC4: korrekt 2FA-kode → fuld token
    @Test
    @Order(9)
    void AC4_totpVerify_medKorrektKode_returnerFuldToken() throws Exception {
        String validCode = totp.generateCurrentCode(Populator.ALICE_TOTP_SECRET);

        fullToken = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + verifyTempToken)
                .body("{\"code\":\"" + validCode + "\"}")
                .post("/auth/2fa/verify")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .extract().path("token");
    }

    // AC4 + fuld adgang: fuld token giver adgang til beskyttede endpoints
    @Test
    @Order(10)
    void fuldToken_kanTilgaaBeskyttetEndpoint() {
        given()
                .header("Authorization", "Bearer " + fullToken)
                .get("/events")
                .then()
                .statusCode(200);
    }

    // ═══════════════════════════════════════════════════
    // FLOW 3 – Login inden for grace period
    // Bruger har totp_enabled=true og gyldig grace period
    // → fuld JWT returneres direkte, ingen 2FA-prompt
    // ═══════════════════════════════════════════════════

    @Test
    @Order(11)
    void loginIndenforGracePeriod_returnerFuldTokenDirekte() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"grace@carebridge.io\", \"password\":\"password123\"}")
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("requires2FA", nullValue())
                .body("requiresTotpSetup", nullValue());
    }
}
