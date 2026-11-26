package net.kdt.pojavlaunch.jcx;

import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

import javax.net.ssl.SSLException;

/**
 * Erro de autenticação com diagnóstico completo: etapa, código, descrição, causa provável e ação.
 * Nenhum token é guardado aqui (o texto técnico passa por {@link JcxAuthLog#sanitize}).
 */
public class JcxAuthError extends RuntimeException {

    public enum Step {
        MICROSOFT("1/6 Conta Microsoft (autorização)"),
        XBOX_LIVE("2/6 Xbox Live"),
        XSTS("3/6 XSTS (permissão Xbox para o Minecraft)"),
        MINECRAFT("4/6 Minecraft Services (login)"),
        PROFILE("5/6 Perfil do Minecraft Java"),
        LICENSE("6/6 Licença / Demo oficial");

        public final String label;
        Step(String l) { label = l; }
    }

    public static final String XBOX_CREATE_PROFILE = "https://start.ui.xboxlive.com/CreateAccount";
    public static final String MC_PROFILE_PAGE = "https://www.minecraft.net/msaprofile/mygames/editprofile";
    public static final String FAMILY_PAGE = "https://account.microsoft.com/family/";

    public final Step step;
    public final int http;          // 0 = não houve resposta HTTP
    public final long xerr;         // 0 = sem XErr
    public final String title, description, cause, action;
    /** Página oficial que resolve o problema (criar perfil Xbox, nome de jogador...). */
    public final String helpUrl;
    public final String helpLabel;
    /** O login já passou da etapa Microsoft: dá para tentar de novo sem pedir a senha. */
    public boolean canRetryWithoutLogin;
    /** Precisa abrir a página de login de novo. */
    public final boolean needsNewLogin;
    public final String technical;

    JcxAuthError(Step step, int http, long xerr, String title, String description, String cause, String action,
                 String helpUrl, String helpLabel, boolean needsNewLogin, String technical, Throwable source) {
        super(title + " — " + step.label + (http > 0 ? " (HTTP " + http + ")" : "") + (xerr != 0 ? " XErr " + xerr : ""), source);
        this.step = step; this.http = http; this.xerr = xerr;
        this.title = title; this.description = description; this.cause = cause; this.action = action;
        this.helpUrl = helpUrl; this.helpLabel = helpLabel; this.needsNewLogin = needsNewLogin;
        this.technical = JcxAuthLog.sanitize(technical);
    }

    /** Resposta HTTP de erro de qualquer etapa. */
    public static JcxAuthError http(Step step, int code, String body) {
        String b = body == null ? "" : body;
        if (step == Step.XSTS && code == 401) return xsts(b);
        if (code == 429)
            return new JcxAuthError(step, code, 0, "Muitas tentativas seguidas",
                    "Os servidores recusaram temporariamente novos logins deste aparelho.",
                    "Limite de requisições da Microsoft/Mojang (HTTP 429).",
                    "Espere alguns minutos e toque em \"Tentar de novo\".", null, null, false, b, null);
        if (code >= 500)
            return new JcxAuthError(step, code, 0, "Serviço oficial instável",
                    "O servidor de " + host(step) + " respondeu com falha interna.",
                    "Instabilidade do serviço (HTTP " + code + "), não é um problema da sua conta.",
                    "Tente de novo em alguns minutos.", null, null, false, b, null);
        if (step == Step.MICROSOFT && (code == 400 || code == 401)) {
            boolean expired = b.contains("invalid_grant") || b.contains("expired");
            return new JcxAuthError(step, code, 0, expired ? "Sessão Microsoft expirada" : "Autorização Microsoft recusada",
                    expired ? "A autorização salva não vale mais (expirou, foi revogada ou a senha foi trocada)."
                            : "A Microsoft não aceitou o código de autorização.",
                    expired ? "Tokens de atualização expiram após um tempo sem uso ou ao trocar a senha."
                            : "O código de login só pode ser usado uma vez e vale poucos minutos.",
                    "Toque em \"Entrar de novo\". Se você marcou \"Manter conectado\", a Microsoft não pede a senha outra vez.",
                    null, null, true, b, null);
        }
        if (step == Step.MINECRAFT && code == 403)
            return new JcxAuthError(step, code, 0, "Minecraft Services recusou o login",
                    "A Mojang não aceitou o token Xbox para este aplicativo.",
                    "Bloqueio do serviço para o cliente de login ou conta com restrição.",
                    "Tente de novo mais tarde. Se continuar, entre em minecraft.net com a mesma conta para conferir se ela está ativa.",
                    "https://www.minecraft.net/login", "Abrir minecraft.net", false, b, null);
        if (step == Step.XBOX_LIVE && (code == 400 || code == 401))
            return new JcxAuthError(step, code, 0, "Xbox Live recusou a conta",
                    "O token da Microsoft não foi aceito pelo Xbox Live.",
                    "Token expirado durante o login ou conta Microsoft bloqueada.",
                    "Entre de novo. Se repetir, entre uma vez em xbox.com com a mesma conta.",
                    "https://www.xbox.com/", "Abrir xbox.com", true, b, null);
        return new JcxAuthError(step, code, 0, "Falha no login",
                "A etapa " + step.label + " respondeu com erro HTTP " + code + ".",
                "Resposta inesperada do serviço oficial.",
                "Tente de novo. Se repetir, copie o diagnóstico e envie junto com o log.", null, null, false, b, null);
    }

    /** Erros XSTS (Xbox) documentados: perfil ausente, país, idade, conta infantil. */
    public static JcxAuthError xsts(String body) {
        long xerr = 0;
        String redirect = null;
        try {
            JSONObject jo = new JSONObject(body);
            xerr = jo.optLong("XErr", 0);
            redirect = jo.optString("Redirect", null);
        } catch (Throwable ignored) {}
        String url;
        switch ((int) (xerr - 2148916000L)) {
            case 233:
                url = safeXboxUrl(redirect);
                return new JcxAuthError(Step.XSTS, 401, xerr, "Falta o perfil Xbox (gratuito)",
                        "Sua conta Microsoft entrou, mas ainda não tem um perfil Xbox. O Minecraft Java usa esse perfil para identificar você.",
                        "Toda conta precisa de um perfil Xbox (gamertag). Ele é GRATUITO; não é preciso assinar Xbox Game Pass nem Xbox Live Gold.",
                        "Toque em \"Criar perfil Xbox\", escolha um gamertag na página oficial e volte aqui: toque em \"Tentar de novo\" — não é preciso digitar a senha de novo.",
                        url, "Criar perfil Xbox", false, body, null);
            case 235:
                return new JcxAuthError(Step.XSTS, 401, xerr, "Xbox Live indisponível no país da conta",
                        "O país/região da conta Microsoft não tem Xbox Live.",
                        "Restrição regional da Microsoft (XErr 2148916235).",
                        "Confira o país em account.microsoft.com. O launcher não consegue contornar isso.",
                        "https://account.microsoft.com/profile/", "Abrir conta Microsoft", false, body, null);
            case 236:
            case 237:
                return new JcxAuthError(Step.XSTS, 401, xerr, "Verificação de idade necessária",
                        "A Microsoft exige verificação de adulto para esta conta.",
                        "Regra legal de alguns países (XErr " + xerr + ").",
                        "Faça a verificação na página da conta Xbox e tente de novo.",
                        "https://account.xbox.com/", "Abrir conta Xbox", false, body, null);
            case 238:
                return new JcxAuthError(Step.XSTS, 401, xerr, "Conta infantil fora de uma Família Microsoft",
                        "Contas de menores de 18 anos precisam ser adicionadas a uma Família Microsoft por um adulto.",
                        "Restrição da conta (XErr 2148916238).",
                        "Peça a um responsável para adicionar a conta em account.microsoft.com/family e liberar o Minecraft; depois toque em \"Tentar de novo\".",
                        FAMILY_PAGE, "Família Microsoft", false, body, null);
            default:
                return new JcxAuthError(Step.XSTS, 401, xerr, "Xbox recusou o acesso ao Minecraft",
                        "O serviço XSTS respondeu com o código " + xerr + ".",
                        "Código de erro Xbox não documentado.",
                        "Tente de novo. Se repetir, entre uma vez em xbox.com com a mesma conta.",
                        "https://www.xbox.com/", "Abrir xbox.com", false, body, null);
        }
    }

    /** Falhas de rede (sem internet, DNS, tempo esgotado, TLS). */
    public static JcxAuthError io(Step step, IOException e) {
        String what, cause;
        if (e instanceof UnknownHostException) { what = "Sem conexão com " + host(step); cause = "Sem internet ou DNS bloqueado."; }
        else if (e instanceof SocketTimeoutException) { what = "Tempo esgotado ao falar com " + host(step); cause = "Internet lenta ou servidor demorando para responder."; }
        else if (e instanceof SSLException) { what = "Conexão segura recusada"; cause = "Data/hora do aparelho errada, rede com proxy/filtro ou Wi-Fi com página de login."; }
        else { what = "Falha de rede"; cause = e.getClass().getSimpleName() + ": " + e.getMessage(); }
        return new JcxAuthError(step, 0, 0, what, "O login parou na etapa " + step.label + ".", cause,
                "Confira a internet (e a data/hora automática) e toque em \"Tentar de novo\".", null, null, false,
                e.toString(), e);
    }

    public static JcxAuthError noJavaProfile() {
        return new JcxAuthError(Step.PROFILE, 404, 0, "Crie seu nome de jogador",
                "A conta tem licença do Minecraft Java (compra ou Game Pass), mas o nome de jogador ainda não foi criado.",
                "Contas novas precisam escolher o nome uma vez no site oficial.",
                "Toque em \"Criar nome de jogador\", escolha o nome em minecraft.net e volte: toque em \"Tentar de novo\".",
                MC_PROFILE_PAGE, "Criar nome de jogador", false, "profile 404 + licença Java", null);
    }

    public static JcxAuthError unexpected(Step step, Throwable t) {
        return new JcxAuthError(step, 0, 0, "Resposta inesperada",
                "A etapa " + step.label + " devolveu dados que o launcher não esperava.",
                t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : ""),
                "Tente de novo. Se repetir, copie o diagnóstico e reporte.", null, null, false, t.toString(), t);
    }

    static String host(Step s) {
        switch (s) {
            case MICROSOFT: return "login.live.com";
            case XBOX_LIVE: return "user.auth.xboxlive.com";
            case XSTS: return "xsts.auth.xboxlive.com";
            default: return "api.minecraftservices.com";
        }
    }

    /** Só aceita links HTTPS de domínios Xbox/Microsoft vindos da resposta. */
    static String safeXboxUrl(String u) {
        if (u == null) return XBOX_CREATE_PROFILE;
        try {
            java.net.URI uri = new java.net.URI(u);
            String h = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if ("https".equalsIgnoreCase(uri.getScheme())
                    && (h.endsWith(".xboxlive.com") || h.endsWith(".xbox.com") || h.equals("xbox.com") || h.endsWith(".microsoft.com")))
                return u;
        } catch (Exception ignored) {}
        return XBOX_CREATE_PROFILE;
    }

    /** Texto completo do diagnóstico (sem tokens), para copiar/compartilhar. */
    public String report() {
        return "JAVA CRAFT X — diagnóstico de login\n"
                + "Etapa: " + step.label + "\n"
                + "Código: " + (http > 0 ? "HTTP " + http : "—") + (xerr != 0 ? " / XErr " + xerr : "") + "\n"
                + "Problema: " + title + "\n"
                + "Descrição: " + description + "\n"
                + "Causa provável: " + cause + "\n"
                + "Ação: " + action + "\n"
                + "Técnico: " + (technical == null || technical.isEmpty() ? "—" : technical);
    }
}
