package net.kdt.pojavlaunch.jcx;

import java.util.regex.Pattern;

/**
 * Remove dados sensíveis (tokens, códigos de autorização) de textos antes de registrar ou mostrar.
 * Usado no diagnóstico de autenticação e nos logs do login Microsoft.
 */
public final class JcxAuthLog {
    private JcxAuthLog() {}

    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+(\\.[A-Za-z0-9_\\-]*)?");
    private static final Pattern KV = Pattern.compile(
            "(?i)(\"?(?:access_token|refresh_token|id_token|token|rpsticket|code|authorization|identitytoken|uhs)\"?\\s*[:=]\\s*\"?)([^\"&,\\s}]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer|XBL3\\.0)\\s+[^\\s\"]+");
    private static final Pattern LONG = Pattern.compile("[A-Za-z0-9_\\-*!$.~+/=]{40,}");

    public static String sanitize(String s) {
        if (s == null) return null;
        String out = JWT.matcher(s).replaceAll("<jwt>");
        out = BEARER.matcher(out).replaceAll("$1 <oculto>");
        out = KV.matcher(out).replaceAll("$1<oculto>");
        out = LONG.matcher(out).replaceAll("<oculto>");
        if (out.length() > 2000) out = out.substring(0, 2000) + "…";
        return out;
    }
}
