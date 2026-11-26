package net.kdt.pojavlaunch.jcx;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Cofre dos tokens das contas (Android Keystore, AES-256-GCM).
 * A chave é gerada dentro do Keystore do aparelho e nunca sai dele; os arquivos das contas
 * guardam só o texto cifrado ("jcxv1:iv:dados"). Senhas Microsoft nunca são vistas nem guardadas:
 * o login acontece na página oficial da Microsoft.
 *
 * Android 5/6.0 sem suporte: o valor fica como antes (armazenamento privado do app).
 * Se a chave for perdida (ex.: dados restaurados em outro aparelho), o token não abre e o
 * launcher pede um novo login, em vez de quebrar.
 */
public final class JcxVault {
    private JcxVault() {}

    public static final String PREFIX = "jcxv1:";
    private static final String ALIAS = "jcx_account_tokens_v1";

    /** Fonte da chave; trocada nos testes por uma chave de software. */
    interface KeySource { SecretKey get() throws Exception; }

    static KeySource sSource = JcxVault::androidKey;
    static volatile boolean sDisabled = false;

    public static boolean isSealed(String v) { return v != null && v.startsWith(PREFIX); }

    /** Cifra o valor. Valores vazios ou "0" (sem token) não são cifrados. */
    public static String seal(String plain) {
        if (plain == null || plain.isEmpty() || "0".equals(plain) || isSealed(plain) || sDisabled) return plain;
        try {
            SecretKey key = sSource.get();
            if (key == null) return plain;
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + hex(iv) + ":" + hex(ct);
        } catch (Throwable t) {
            warn("Keystore indisponível; mantendo armazenamento privado do app: " + t.getClass().getSimpleName());
            return plain;
        }
    }

    /** Abre o valor; devolve null se estava cifrado e não pôde ser aberto. */
    public static String open(String v) {
        if (!isSealed(v)) return v;
        try {
            String body = v.substring(PREFIX.length());
            int sep = body.indexOf(':');
            byte[] iv = unhex(body.substring(0, sep));
            byte[] ct = unhex(body.substring(sep + 1));
            SecretKey key = sSource.get();
            if (key == null) return null;
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            warn("Token não pôde ser aberto (" + t.getClass().getSimpleName() + "); será pedido novo login");
            return null;
        }
    }

    private static void warn(String m) {
        try { Log.w("JcxVault", m); } catch (Throwable ignored) {}
    }

    private static SecretKey androidKey() throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            KeyStore.Entry e = ks.getEntry(ALIAS, null);
            if (e instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry) e).getSecretKey();
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    static String hex(byte[] b) {
        char[] d = "0123456789abcdef".toCharArray();
        char[] o = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) { o[i * 2] = d[(b[i] >> 4) & 15]; o[i * 2 + 1] = d[b[i] & 15]; }
        return new String(o);
    }

    static byte[] unhex(String s) {
        byte[] o = new byte[s.length() / 2];
        for (int i = 0; i < o.length; i++) o[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return o;
    }
}
