package net.kdt.pojavlaunch.jcx;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CRASH ANALYZER: regras sobre o texto dos logs. Cada achado é uma POSSÍVEL causa,
 * acompanhada da linha do log que a motivou — nunca um diagnóstico confirmado.
 */
public final class CrashAnalyzer {
    private CrashAnalyzer() {}

    public enum Level { HIGH, MEDIUM, LOW }

    public static final class Rule {
        final String id, title, explanation, fix;
        final Level level;
        final Pattern pattern;
        Rule(String id, Level level, String regex, String title, String explanation, String fix) {
            this.id = id; this.level = level; this.title = title; this.explanation = explanation; this.fix = fix;
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        }
    }

    public static final class Finding {
        public String id, title, explanation, fix, evidence, source;
        public Level level;
        public int line;
    }

    public static final class Result {
        public final List<Finding> findings = new ArrayList<>();
        public String stage;
        public String javaVersion;
        public String mcVersion;
    }

    private static final List<Rule> RULES = new ArrayList<>();
    static {
        RULES.add(new Rule("oom", Level.HIGH, "java\\.lang\\.OutOfMemoryError.*$",
                "Memória Java esgotada",
                "O heap configurado acabou durante o jogo (mundo grande, muitos mods ou render distance alta).",
                "No X-PERFORMANCE aumente o heap até o máximo seguro OU use o modo ECO; reduza a distância de renderização; remova mods pesados."));
        RULES.add(new Rule("heap_too_big", Level.HIGH, "(Could not reserve enough space.*|Invalid maximum heap size.*|Error occurred during initialization of VM.*|Cannot allocate memory.*)$",
                "Heap maior do que o aparelho consegue reservar",
                "A JVM não conseguiu reservar a memória pedida.",
                "Reduza o heap no X-PERFORMANCE (use o valor recomendado) e feche outros apps."));
        RULES.add(new Rule("java_version", Level.HIGH, "(UnsupportedClassVersionError.*|has been compiled by a more recent version of the Java Runtime.*|requires Java \\d+.*|Java \\d+ or (?:newer|later) is required.*)$",
                "Versão do Java incompatível",
                "O jogo ou um mod foi compilado para um Java mais novo do que o runtime usado.",
                "Deixe o runtime em automático no perfil (1.17+ usa Java 17, 1.20.5+ usa Java 21) ou instale o runtime certo nas configurações Java."));
        RULES.add(new Rule("missing_dep", Level.HIGH, "(Incompatible mods? found.*|Mod resolution failed.*|Missing or unsupported mandatory dependencies.*|requires (?:any version|version .*) of .*|depends on .* which is missing.*|MissingModsException.*|Could not find required mod.*)$",
                "Dependência de mod ausente ou incompatível",
                "Um mod precisa de outro mod (ou de outra versão dele) que não está instalado.",
                "Abra o Mod Center → Instalados e reinstale o mod: as dependências obrigatórias são instaladas junto. Confira a versão do loader."));
        RULES.add(new Rule("duplicate", Level.HIGH, "(Duplicate mods? found.*|DuplicateModsFoundException.*|found duplicate mod.*|Mod ID .* is provided by multiple.*)$",
                "Mod duplicado",
                "O mesmo mod está na pasta mods mais de uma vez (versões diferentes).",
                "No Mod Center → Instalados, remova ou desative uma das cópias."));
        RULES.add(new Rule("mixin", Level.MEDIUM, "(Mixin apply (?:for mod .* )?failed.*|MixinApplyError.*|InvalidMixinException.*|Critical injection failure.*|MixinTransformerError.*)$",
                "Conflito entre mods (Mixin)",
                "Dois mods tentam modificar o mesmo código do jogo, ou um mod não é desta versão do Minecraft.",
                "Desative os mods citados na linha do erro, um por vez, até o jogo abrir."));
        RULES.add(new Rule("class_missing", Level.MEDIUM, "(java\\.lang\\.NoClassDefFoundError.*|java\\.lang\\.ClassNotFoundException.*|java\\.lang\\.NoSuchMethodError.*|java\\.lang\\.NoSuchFieldError.*|java\\.lang\\.AbstractMethodError.*)$",
                "Mod feito para outra versão ou loader",
                "Código esperado por um mod não existe nesta versão do jogo/loader.",
                "Verifique se todos os mods são para a mesma versão do Minecraft e o mesmo loader do perfil."));
        RULES.add(new Rule("graphics", Level.MEDIUM, "(GLFW error.*|No OpenGL context.*|OpenGL .*not supported.*|Pixel format not accelerated.*|Failed to create (?:the )?(?:OpenGL )?(?:context|window|display).*|Couldn't set pixel format.*|GL_OUT_OF_MEMORY.*|EGL_BAD.*|vkCreate.* failed.*|VK_ERROR.*)$",
                "Falha gráfica / renderizador",
                "O renderizador escolhido não conseguiu criar o contexto gráfico ou a GPU recusou uma operação.",
                "Edite o perfil e troque o renderizador (Krypton ↔ MobileGlues ↔ Zink). Desative shaders e reduza a resolução."));
        RULES.add(new Rule("shader", Level.MEDIUM, "(Failed to (?:load|compile|link) shader.*|shader .*compil.*(?:error|fail).*|Error compiling (?:program|shader).*|ShaderCompileException.*|Iris.*(?:error|failed).*)$",
                "Shader incompatível",
                "O shaderpack usa recursos que o renderizador/GPU do Android não oferece.",
                "Desative o shader (tecla O no Iris / menu Shaders) ou remova-o no Shader Lab."));
        RULES.add(new Rule("native", Level.MEDIUM, "(SIGSEGV.*|SIGABRT.*|Fatal signal \\d+.*|A fatal error has been detected by the Java Runtime.*|signal 11.*)$",
                "Falha nativa (driver gráfico ou biblioteca nativa)",
                "O processo caiu fora do Java — normalmente o driver da GPU ou o renderizador.",
                "Troque o renderizador do perfil, reduza a resolução e teste sem mods de renderização (Sodium, Iris)."));
        RULES.add(new Rule("sodium", Level.LOW, "^.*(?:me\\.jellysquid\\.mods\\.sodium|net\\.caffeinemc\\.mods\\.sodium).*(?:Exception|Error).*$",
                "Sodium envolvido no erro",
                "O Sodium aparece na pilha do erro; ele tem problemas conhecidos com alguns renderizadores Android.",
                "Teste sem o Sodium (Mod Center → Desativar)."));
        RULES.add(new Rule("network", Level.MEDIUM, "(java\\.net\\.UnknownHostException.*|java\\.net\\.SocketTimeoutException.*|java\\.net\\.ConnectException.*|SSLHandshakeException.*|Failed to download .*)$",
                "Problema de conexão",
                "Um download (bibliotecas, assets ou login) falhou por falta de rede.",
                "Verifique a internet e tente de novo; os downloads são retomados."));
        RULES.add(new Rule("storage", Level.HIGH, "(No space left on device.*|ENOSPC.*)$",
                "Armazenamento cheio",
                "Não há espaço para gravar arquivos do jogo.",
                "Libere espaço no aparelho (mundos antigos, backups, versões não usadas)."));
        RULES.add(new Rule("corrupt", Level.MEDIUM, "(java\\.util\\.zip\\.ZipException.*|invalid (?:LOC|CEN) header.*|zip END header not found.*|error in opening zip file.*|Unexpected end of ZLIB input stream.*)$",
                "Arquivo corrompido",
                "Um .jar (biblioteca, versão ou mod) está incompleto ou danificado.",
                "Reinstale a versão (apague a pasta da versão para baixar de novo) ou reinstale o mod citado."));
        RULES.add(new Rule("session", Level.MEDIUM, "(Invalid session.*|multiplayer\\.disconnect\\.invalid_session.*|Failed to (?:verify|authenticate) (?:username|session).*|401 Unauthorized.*)$",
                "Sessão de login inválida",
                "O token da conta expirou ou não é aceito pelo servidor.",
                "Remova e adicione novamente a conta Microsoft. Contas locais/demo não entram em servidores online."));
        RULES.add(new Rule("exitcode", Level.LOW, "((?:exit code|exitCode|Process exited with code)[:= ]+-?\\d+.*)$",
                "Código de saída registrado",
                "O jogo terminou com um código de saída. Sozinho ele não indica a causa.",
                "Veja os outros itens desta análise ou compartilhe o log completo."));
    }

    /** Analisa um texto de log; {@code source} identifica o arquivo. */
    public static void analyze(String text, String source, Result into) {
        if (text == null) return;
        for (Rule r : RULES) {
            boolean already = false;
            for (Finding f : into.findings) if (f.id.equals(r.id)) { already = true; break; }
            if (already) continue;
            Matcher m = r.pattern.matcher(text);
            if (m.find()) {
                Finding f = new Finding();
                f.id = r.id; f.title = r.title; f.explanation = r.explanation; f.fix = r.fix; f.level = r.level;
                String ev = m.group().trim();
                if (ev.length() > 300) ev = ev.substring(0, 300) + "…";
                f.evidence = ev;
                f.source = source;
                f.line = lineOf(text, m.start());
                into.findings.add(f);
            }
        }
        if (into.stage == null) into.stage = stage(text);
        if (into.javaVersion == null) {
            Matcher jm = Pattern.compile("(?:Java Version|java\\.version|Java is)[^\\n]*?(\\d+(?:\\.\\d+)*(?:_\\d+)?)").matcher(text);
            if (jm.find()) into.javaVersion = jm.group(1);
        }
        if (into.mcVersion == null) {
            Matcher mm = Pattern.compile("Minecraft Version(?: ID)?:\\s*([^\\s]+)").matcher(text);
            if (mm.find()) into.mcVersion = mm.group(1);
        }
    }

    private static int lineOf(String text, int pos) {
        int line = 1;
        for (int i = 0; i < pos && i < text.length(); i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    /** Estimativa da última etapa alcançada, pela presença de marcos conhecidos do log. */
    static String stage(String t) {
        String s = "Antes da JVM iniciar (nenhum marco do jogo no log)";
        if (contains(t, "Launching Minecraft|Info: Launcher version|JVM args|Java Home|-Xmx")) s = "Runtime Java iniciado";
        if (contains(t, "Setting user:|Loading Minecraft|Loading for game|ModLauncher running|Fabric Loader|Quilt Loader|Backend library: LWJGL|LWJGL Version")) s = "Inicialização do Minecraft";
        if (contains(t, "OpenGL|GL_RENDERER|Created: \\d+x\\d+|Reloading ResourceManager|textures-atlas|Sound engine started")) s = "Renderizador e recursos carregados";
        if (contains(t, "Preparing spawn area|Starting integrated minecraft server|Joining world|Connecting to |Loaded \\d+ advancements|logged in with entity id")) s = "Dentro do mundo / servidor";
        return s;
    }

    private static boolean contains(String t, String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(t).find();
    }

    public static String levelLabel(Level l) {
        switch (l) {
            case HIGH: return "Possível causa — evidência forte";
            case MEDIUM: return "Possível causa — evidência moderada";
            default: return "Informação";
        }
    }
}
