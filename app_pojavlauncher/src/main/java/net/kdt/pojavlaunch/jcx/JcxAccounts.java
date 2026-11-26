package net.kdt.pojavlaunch.jcx;

import android.content.Context;

import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.MinecraftAccount;

/**
 * Estado das contas para a interface (Home, Central de Contas, botão JOGAR).
 * Não muda nada no login: só lê o que já está salvo.
 */
public final class JcxAccounts {
    private JcxAccounts() {}

    /** Chaves do ExtraCore ouvidas pelo seletor de contas (mcAccountSpinner). */
    public static final String EXTRA_RETRY_LOGIN = "jcx_retry_login";
    public static final String EXTRA_SELECT_ACCOUNT = "jcx_select_account";
    public static final String EXTRA_REFRESH_ACCOUNT = "jcx_refresh_account";
    public static final String EXTRA_ACCOUNTS_CHANGED = "jcx_accounts_changed";

    public static final String PREF_GUEST = "jcx_guest_mode";

    public enum Mode { NONE, GUEST, FULL, DEMO, LOCAL }

    public static Mode mode(Context c) {
        MinecraftAccount a = current(c);
        if (a == null) return isGuest() ? Mode.GUEST : Mode.NONE;
        if (a.isDemo()) return Mode.DEMO;
        if (a.isLocal()) return Mode.LOCAL;
        return Mode.FULL;
    }

    public static MinecraftAccount current(Context c) {
        try {
            String n = PojavProfile.getCurrentProfileName(c);
            if (n == null || n.isEmpty()) return null;
            return MinecraftAccount.load(n);
        } catch (Throwable t) { return null; }
    }

    public static boolean isGuest() {
        return LauncherPreferences.DEFAULT_PREF != null && LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_GUEST, false);
    }

    public static void setGuest(boolean on) {
        if (LauncherPreferences.DEFAULT_PREF != null) LauncherPreferences.DEFAULT_PREF.edit().putBoolean(PREF_GUEST, on).apply();
    }

    /** Linha curta para a Home. */
    public static String homeLine(Context c) {
        MinecraftAccount a = current(c);
        String pending = JcxAuthStore.hasPending() ? "  •  ⚠ login pendente (Contas)" : "";
        switch (mode(c)) {
            case FULL: return "Conta: " + a.username + "  •  JOGO COMPLETO" + pending;
            case DEMO: return "Conta Microsoft sem o Java  •  DEMO OFICIAL (~100 min)" + pending;
            case LOCAL: return "Perfil local: " + a.username + " (offline)" + pending;
            case GUEST: return "Modo convidado  •  entre com a Microsoft para jogar" + pending;
            default: return "Nenhuma conta  •  toque em Contas" + pending;
        }
    }

    /** Texto do botão principal. */
    public static String playLabel(Context c) {
        switch (mode(c)) {
            case DEMO: return "JOGAR DEMO";
            case GUEST:
            case NONE: return "ENTRAR PARA JOGAR";
            default: return "JOGAR";
        }
    }

    /** Situação da sessão de uma conta Microsoft. */
    public static String sessionState(MinecraftAccount a) {
        if (a == null) return "—";
        if (a.isLocal()) return "Offline (sem token)";
        if ("0".equals(a.msaRefreshToken)) return "Autorização indisponível — entre de novo";
        long left = a.expiresAt - System.currentTimeMillis();
        if (left > 0) return "Sessão ativa (renova sozinha em " + Math.max(1, left / 3600000L) + " h)";
        return "Sessão será renovada automaticamente no próximo uso (sem senha)";
    }
}
