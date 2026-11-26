package net.kdt.pojavlaunch.authenticator.microsoft;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.listener.DoneListener;
import net.kdt.pojavlaunch.authenticator.listener.ErrorListener;
import net.kdt.pojavlaunch.authenticator.listener.ProgressListener;
import net.kdt.pojavlaunch.jcx.JcxAuthError;
import net.kdt.pojavlaunch.jcx.JcxAuthError.Step;
import net.kdt.pojavlaunch.jcx.JcxAuthLog;
import net.kdt.pojavlaunch.jcx.JcxAuthStore;
import net.kdt.pojavlaunch.jcx.JcxOwnership;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Login Microsoft → Xbox Live → XSTS → Minecraft Services → perfil → licença/demo.
 *
 * JAVA CRAFT X:
 *  - cada etapa é rastreada; falhas viram {@link JcxAuthError} com etapa, código, causa e ação;
 *  - nenhum token/código é escrito no log;
 *  - conexões com tempo limite (antes podiam travar para sempre);
 *  - se a falha acontece depois da etapa Microsoft, a autorização fica guardada (cifrada) para
 *    "Tentar de novo" sem pedir a senha;
 *  - licença Java x Bedrock x Demo classificada por {@link JcxOwnership} (antes, contas só com
 *    Bedrock eram tratadas como donas do Java e ficavam presas no erro "nome de usuário");
 *  - dois logins iguais ao mesmo tempo não rodam em paralelo.
 */
public class MicrosoftBackgroundLogin {
    private static final String TAG = "MicroAuth";
    private static final String authTokenUrl = "https://login.live.com/oauth20_token.srf";
    private static final String xblAuthUrl = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String xstsAuthUrl = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String mcLoginUrl = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String mcProfileUrl = "https://api.minecraftservices.com/minecraft/profile";
    private static final String mcStoreUrl = "https://api.minecraftservices.com/entitlements/mcstore";
    private static final int CONNECT_TIMEOUT = 15000, READ_TIMEOUT = 25000;

    private static final Set<String> sInFlight = Collections.synchronizedSet(new HashSet<>());

    private final boolean mIsRefresh;
    private final String mAuthCode;

    /* Fields used to fill the account  */
    public String msRefreshToken;
    public String mcName;
    public String mcToken;
    public String mcUuid;
    public boolean doesOwnGame, hasProfile = false;
    public long expiresAt;
    public JcxOwnership.Result ownership;

    public MicrosoftBackgroundLogin(boolean isRefresh, String authCode){
        mIsRefresh = isRefresh;
        mAuthCode = authCode;
    }

    /** Performs a full login, calling back listeners appropriately  */
    public void performLogin(@Nullable final ProgressListener progressListener,
                             @Nullable final DoneListener doneListener,
                             @Nullable final ErrorListener errorListener){
        final String key = (mIsRefresh ? "r" : "c") + (mAuthCode == null ? 0 : mAuthCode.hashCode());
        if (!sInFlight.add(key)) {
            Log.i(TAG, "Login idêntico já em andamento; ignorando pedido duplicado");
            return;
        }
        sExecutorService.execute(() -> {
            Step step = Step.MICROSOFT;
            boolean pastMicrosoft = false;
            try {
                if (mAuthCode == null || mAuthCode.isEmpty() || "0".equals(mAuthCode))
                    throw JcxAuthError.http(Step.MICROSOFT, 400, "invalid_grant: sem autorização salva");
                notifyProgress(progressListener, 1);
                String accessToken = acquireAccessToken(mIsRefresh, mAuthCode);
                pastMicrosoft = true;
                step = Step.XBOX_LIVE;
                notifyProgress(progressListener, 2);
                String xboxLiveToken = acquireXBLToken(accessToken);
                step = Step.XSTS;
                notifyProgress(progressListener, 3);
                String[] xsts = acquireXsts(xboxLiveToken);
                step = Step.MINECRAFT;
                notifyProgress(progressListener, 4);
                String mcToken = acquireMinecraftToken(xsts[0], xsts[1]);
                step = Step.PROFILE;
                notifyProgress(progressListener, 5);
                checkMcProfile(mcToken);
                step = Step.LICENSE;
                List<String> entitlements = hasProfile ? Collections.emptyList() : fetchOwnedItems(mcToken);
                ownership = JcxOwnership.classify(entitlements, hasProfile);
                Log.i(TAG, "Licença: " + ownership.kind);

                if (ownership.kind == JcxOwnership.Kind.JAVA_NO_PROFILE) {
                    throw JcxAuthError.noJavaProfile();
                } else if (ownership.isDemo()) {
                    doesOwnGame = false;
                    mcName = "Demo.Player";
                    mcUuid = "00000000-0000-0000-0000-000000000000";
                } else {
                    doesOwnGame = true;
                    if (mcName == null || mcUuid == null)
                        throw JcxAuthError.unexpected(Step.PROFILE, new IllegalStateException("perfil sem nome/uuid"));
                }

                MinecraftAccount acc = MinecraftAccount.load(mcName);
                if(acc == null) acc = new MinecraftAccount();
                acc.xuid = xsts[0];
                acc.clientToken = "0"; /* FIXME */
                acc.accessToken = mcToken;
                acc.username = mcName;
                acc.profileId = mcUuid;
                acc.isMicrosoft = true;
                acc.msaRefreshToken = msRefreshToken;
                acc.expiresAt = expiresAt;
                acc.updateSkinFace();
                acc.save();
                JcxAuthStore.clearPending();
                JcxAuthStore.recordSuccess(acc.username, ownership.kind);

                if(doneListener != null) {
                    MinecraftAccount finalAcc = acc;
                    Tools.runOnUiThread(() -> doneListener.onLoginDone(finalAcc));
                }

            } catch (Throwable e) {
                JcxAuthError err;
                if (e instanceof JcxAuthError) err = (JcxAuthError) e;
                else if (e instanceof IOException) err = JcxAuthError.io(step, (IOException) e);
                else err = JcxAuthError.unexpected(step, e);
                if (pastMicrosoft && msRefreshToken != null && !err.needsNewLogin) {
                    JcxAuthStore.savePending(msRefreshToken);
                    err.canRetryWithoutLogin = true;
                }
                JcxAuthStore.saveLastError(err.report());
                Log.e(TAG, "Login falhou: " + JcxAuthLog.sanitize(err.getMessage()));
                if(errorListener != null) {
                    final JcxAuthError fe = err;
                    Tools.runOnUiThread(() -> errorListener.onLoginError(fe));
                }
            } finally {
                sInFlight.remove(key);
                ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE_MICROSOFT);
            }
        });
    }

    private static HttpURLConnection open(String u) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setUseCaches(false);
        return conn;
    }

    private static void post(HttpURLConnection conn, String body) throws IOException {
        try (OutputStream wr = conn.getOutputStream()) {
            wr.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    public String acquireAccessToken(boolean isRefresh, String authcode) throws IOException, JSONException {
        Log.i(TAG, "Etapa 1: token Microsoft (" + (isRefresh ? "renovação" : "novo login") + ")");
        String formData = convertToFormData(
                "client_id", "00000000402b5328",
                isRefresh ? "refresh_token" : "code", authcode,
                "grant_type", isRefresh ? "refresh_token" : "authorization_code",
                "redirect_url", "https://login.live.com/oauth20_desktop.srf",
                "scope", "service::user.auth.xboxlive.com::MBI_SSL"
        );

        HttpURLConnection conn = open(authTokenUrl);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("charset", "utf-8");
        conn.setRequestProperty("Content-Length", Integer.toString(formData.getBytes(StandardCharsets.UTF_8).length));
        conn.setRequestMethod("POST");
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.connect();
        post(conn, formData);
        int code = conn.getResponseCode();
        if(code >= 200 && code < 300) {
            JSONObject jo = new JSONObject(Tools.read(conn.getInputStream()));
            msRefreshToken = jo.getString("refresh_token");
            conn.disconnect();
            return jo.getString("access_token");
        }
        throw httpError(Step.MICROSOFT, conn);
    }

    private String acquireXBLToken(String accessToken) throws IOException, JSONException {
        JSONObject data = new JSONObject();
        JSONObject properties = new JSONObject();
        properties.put("AuthMethod", "RPS");
        properties.put("SiteName", "user.auth.xboxlive.com");
        properties.put("RpsTicket", accessToken);
        data.put("Properties",properties);
        data.put("RelyingParty", "http://auth.xboxlive.com");
        data.put("TokenType", "JWT");

        String req = data.toString();
        HttpURLConnection conn = open(xblAuthUrl);
        setCommonProperties(conn, req);
        conn.connect();
        post(conn, req);
        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(Tools.read(conn.getInputStream()));
            conn.disconnect();
            Log.i(TAG, "Etapa 2: Xbox Live OK");
            return jo.getString("Token");
        }
        throw httpError(Step.XBOX_LIVE, conn);
    }

    /** @return [uhs, token]*/
    private @NonNull String[] acquireXsts(String xblToken) throws IOException, JSONException {
        JSONObject data = new JSONObject();
        JSONObject properties = new JSONObject();
        properties.put("SandboxId", "RETAIL");
        properties.put("UserTokens", new JSONArray(Collections.singleton(xblToken)));
        data.put("Properties", properties);
        data.put("RelyingParty", "rp://api.minecraftservices.com/");
        data.put("TokenType", "JWT");

        String req = data.toString();
        HttpURLConnection conn = open(xstsAuthUrl);
        setCommonProperties(conn, req);
        conn.connect();
        post(conn, req);

        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(Tools.read(conn.getInputStream()));
            String uhs = jo.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs");
            String token = jo.getString("Token");
            conn.disconnect();
            Log.i(TAG, "Etapa 3: XSTS OK");
            return new String[]{uhs, token};
        }
        throw httpError(Step.XSTS, conn);
    }

    private String acquireMinecraftToken(String xblUhs, String xblXsts) throws IOException, JSONException {
        JSONObject data = new JSONObject();
        data.put("identityToken", "XBL3.0 x=" + xblUhs + ";" + xblXsts);

        String req = data.toString();
        HttpURLConnection conn = open(mcLoginUrl);
        setCommonProperties(conn, req);
        conn.connect();
        post(conn, req);

        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(Tools.read(conn.getInputStream()));
            conn.disconnect();
            long expiresIn = jo.optLong("expires_in", 86400);
            // margem de 5 min para renovar antes de vencer
            expiresAt = System.currentTimeMillis() + Math.max(600, expiresIn - 300) * 1000L;
            mcToken = jo.getString("access_token");
            Log.i(TAG, "Etapa 4: Minecraft Services OK");
            return mcToken;
        }
        throw httpError(Step.MINECRAFT, conn);
    }

    /** Lê os direitos oficiais (nomes dos itens e, se presente, a lista assinada). */
    private List<String> fetchOwnedItems(String mcAccessToken) throws IOException {
        String s = "";
        // minecraftservices pode oscilar: tenta de novo com espera crescente
        for (int retryCount = 0; retryCount < 5; ++retryCount) {
            HttpURLConnection conn = open(mcStoreUrl);
            conn.setRequestProperty("Authorization", "Bearer " + mcAccessToken);
            conn.connect();
            if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                s = Tools.read(conn.getInputStream());
                conn.disconnect();
                break;
            } else if (retryCount == 4) {
                throw httpError(Step.LICENSE, conn);
            }
            sleep(retryCount);
        }
        List<String> names = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(s);
            JSONArray items = root.optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it != null) names.add(it.optString("name"));
            }
            String jwt = root.optString("signature", "");
            String[] parts = jwt.split("\\.");
            if (parts.length > 1) {
                JSONObject body = new JSONObject(new String(Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING), StandardCharsets.UTF_8));
                JSONArray ents = body.optJSONArray("entitlements");
                if (ents != null) for (int i = 0; i < ents.length(); i++) {
                    Object o = ents.opt(i);
                    if (o instanceof String) names.add((String) o);
                    else if (o instanceof JSONObject) names.add(((JSONObject) o).optString("name"));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Resposta de licença sem itens reconhecidos; tratando como sem licença Java (Demo)");
        }
        Log.i(TAG, "Etapa 6: " + names.size() + " direito(s) encontrados");
        return names;
    }

    private void checkMcProfile(String mcAccessToken) throws IOException, JSONException {
        for (int retryCount = 0; retryCount < 5; ++retryCount) {
            HttpURLConnection conn = open(mcProfileUrl);
            conn.setRequestProperty("Authorization", "Bearer " + mcAccessToken);
            conn.connect();
            int code = conn.getResponseCode();
            if(code >= 200 && code < 300) {
                String s = Tools.read(conn.getInputStream());
                conn.disconnect();
                JSONObject jsonObject = new JSONObject(s);
                String name = jsonObject.getString("name");
                String uuid = jsonObject.getString("id");
                mcUuid = uuid.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
                mcName = name;
                doesOwnGame = true;
                hasProfile = true;
                Log.i(TAG, "Etapa 5: perfil Java encontrado (" + name + ")");
                return;
            } else if (code == 404){
                Log.i(TAG, "Etapa 5: conta sem perfil Java; verificando licença");
                hasProfile = false;
                conn.disconnect();
                return;
            } else if (code == 401 || code == 403 || retryCount == 4) {
                throw httpError(Step.PROFILE, conn);
            }
            sleep(retryCount);
        }
    }

    private static void sleep(int retryCount) {
        try { Thread.sleep(500L * (1L << retryCount)); // 0.5s, 1s, 2s, 4s
        } catch (InterruptedException ignored) {}
    }

    /** Wrapper to ease notifying the listener */
    private void notifyProgress(@Nullable ProgressListener listener, int step){
        if(listener != null){
            Tools.runOnUiThread(() -> listener.onLoginProgress(step));
        }
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE_MICROSOFT, step*20);
    }

    /** Set common properties for the connection. Given that all requests are POST, interactivity is always enabled */
    private static void setCommonProperties(HttpURLConnection conn, String formData) throws IOException {
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("charset", "utf-8");
        conn.setRequestProperty("Content-Length", Integer.toString(formData.getBytes(StandardCharsets.UTF_8).length));
        conn.setRequestMethod("POST");
        conn.setDoInput(true);
        conn.setDoOutput(true);
    }

    /**
     * @param data A series a strings: key1, value1, key2, value2...
     * @return the data converted as a form string for a POST request
     */
    private static String convertToFormData(String... data) throws UnsupportedEncodingException {
        StringBuilder builder = new StringBuilder();
        for(int i=0; i<data.length; i+=2){
            if (builder.length() > 0) builder.append("&");
            builder.append(URLEncoder.encode(data[i], "UTF-8"))
                    .append("=")
                    .append(URLEncoder.encode(data[i+1], "UTF-8"));
        }
        return builder.toString();
    }

    private static JcxAuthError httpError(Step step, HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        String body = "";
        try (InputStream es = conn.getErrorStream()) {
            if (es != null) body = Tools.read(es);
        } catch (Exception ignored) {}
        conn.disconnect();
        Log.w(TAG, step.label + " → HTTP " + code + " " + JcxAuthLog.sanitize(body));
        return JcxAuthError.http(step, code, body);
    }
}
