package net.kdt.pojavlaunch.jcx;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Test;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Login Microsoft: licença x demo, diagnóstico de erros, sanitização e cofre de tokens. */
public class JcxAuthTest {

    private static final JcxVault.KeySource ORIGINAL = JcxVault.sSource;

    @After
    public void resetVault() { JcxVault.sSource = ORIGINAL; }

    @Test
    public void profileMeansFullGame() {
        assertEquals(JcxOwnership.Kind.JAVA_FULL, JcxOwnership.classify(Collections.emptyList(), true).kind);
    }

    @Test
    public void bedrockOnlyIsDemoNotOwner() {
        JcxOwnership.Result r = JcxOwnership.classify(Arrays.asList("product_minecraft_bedrock", "game_minecraft_bedrock"), false);
        assertEquals(JcxOwnership.Kind.BEDROCK_ONLY_DEMO, r.kind);
        assertTrue(r.isDemo());
    }

    @Test
    public void javaLicenseWithoutProfileAsksForName() {
        assertEquals(JcxOwnership.Kind.JAVA_NO_PROFILE,
                JcxOwnership.classify(Arrays.asList("product_minecraft", "game_minecraft", "product_minecraft_bedrock"), false).kind);
        assertEquals(JcxOwnership.Kind.JAVA_NO_PROFILE,
                JcxOwnership.classify(Collections.singletonList("product_game_pass_pc"), false).kind);
    }

    @Test
    public void nothingIsDemo() {
        JcxOwnership.Result r = JcxOwnership.classify(Collections.emptyList(), false);
        assertEquals(JcxOwnership.Kind.DEMO, r.kind);
        assertTrue(r.isDemo());
        assertTrue(JcxOwnership.classify(null, false).isDemo());
    }

    @Test
    public void xstsNoXboxProfileExplainsFreeProfile() {
        JcxAuthError e = JcxAuthError.http(JcxAuthError.Step.XSTS, 401,
                "{\"Identity\":\"0\",\"XErr\":2148916233,\"Message\":\"\",\"Redirect\":\"https://start.ui.xboxlive.com/CreateAccount\"}");
        assertEquals(2148916233L, e.xerr);
        assertEquals(JcxAuthError.Step.XSTS, e.step);
        assertEquals("https://start.ui.xboxlive.com/CreateAccount", e.helpUrl);
        assertTrue(e.cause.contains("GRATUITO"));
        assertFalse(e.needsNewLogin);
    }

    @Test
    public void xstsRedirectFromUntrustedHostIsReplaced() {
        JcxAuthError e = JcxAuthError.xsts("{\"XErr\":2148916233,\"Redirect\":\"http://evil.example/x\"}");
        assertEquals(JcxAuthError.XBOX_CREATE_PROFILE, e.helpUrl);
    }

    @Test
    public void xstsChildAccountPointsToFamily() {
        JcxAuthError e = JcxAuthError.xsts("{\"XErr\":2148916238}");
        assertEquals(JcxAuthError.FAMILY_PAGE, e.helpUrl);
    }

    @Test
    public void expiredRefreshNeedsNewLogin() {
        JcxAuthError e = JcxAuthError.http(JcxAuthError.Step.MICROSOFT, 400,
                "{\"error\":\"invalid_grant\",\"error_description\":\"The provided value for the input parameter 'refresh_token' is not valid.\"}");
        assertTrue(e.needsNewLogin);
        assertEquals("Sessão Microsoft expirada", e.title);
    }

    @Test
    public void rateLimitAndServerErrors() {
        assertEquals("Muitas tentativas seguidas", JcxAuthError.http(JcxAuthError.Step.MINECRAFT, 429, "").title);
        assertEquals("Serviço oficial instável", JcxAuthError.http(JcxAuthError.Step.PROFILE, 503, "").title);
    }

    @Test
    public void networkErrorsAreNamed() {
        assertTrue(JcxAuthError.io(JcxAuthError.Step.XBOX_LIVE, new UnknownHostException("user.auth.xboxlive.com")).title.startsWith("Sem conexão"));
        assertTrue(JcxAuthError.io(JcxAuthError.Step.MINECRAFT, new SocketTimeoutException()).title.startsWith("Tempo esgotado"));
    }

    @Test
    public void reportNeverContainsTokens() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        String body = "{\"access_token\":\"" + jwt + "\",\"refresh_token\":\"M.C507_BL2.0.U.-Cnf*abc!def$ghi\",\"code\":\"M.R3_BAY.abcdef\"}";
        JcxAuthError e = JcxAuthError.http(JcxAuthError.Step.MINECRAFT, 400, body);
        String r = e.report();
        assertFalse(r.contains(jwt));
        assertFalse(r.contains("M.C507_BL2"));
        assertFalse(r.contains("M.R3_BAY"));
        assertTrue(r.contains("Etapa: 4/6"));
    }

    @Test
    public void sanitizeBearerAndLongStrings() {
        String s = JcxAuthLog.sanitize("Authorization: Bearer abc.def.ghi and XBL3.0 x=123;tok and "
                + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        assertFalse(s.contains("abc.def.ghi"));
        assertFalse(s.contains("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
        assertNull(JcxAuthLog.sanitize(null));
    }

    @Test
    public void vaultRoundTripWithSoftwareKey() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey k = kg.generateKey();
        JcxVault.sSource = () -> k;
        String token = "M.C507_BL2.0.U.-Cnf*refresh!token$with:special/chars=";
        String sealed = JcxVault.seal(token);
        assertTrue(JcxVault.isSealed(sealed));
        assertFalse(sealed.contains("refresh"));
        assertEquals(token, JcxVault.open(sealed));
        // não cifra "sem token" nem cifra duas vezes
        assertEquals("0", JcxVault.seal("0"));
        assertEquals(sealed, JcxVault.seal(sealed));
        // texto antigo (não cifrado) continua legível
        assertEquals("plain", JcxVault.open("plain"));
    }

    @Test
    public void vaultWrongKeyGivesNullNotGarbage() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey k1 = kg.generateKey(), k2 = kg.generateKey();
        JcxVault.sSource = () -> k1;
        String sealed = JcxVault.seal("secret");
        JcxVault.sSource = () -> k2;
        assertNull(JcxVault.open(sealed));
    }

    @Test
    public void vaultWithoutKeyKeepsValue() {
        JcxVault.sSource = () -> null;
        assertEquals("abc", JcxVault.seal("abc"));
    }
}
