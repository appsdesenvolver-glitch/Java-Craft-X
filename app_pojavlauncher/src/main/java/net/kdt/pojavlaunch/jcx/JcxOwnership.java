package net.kdt.pojavlaunch.jcx;

import java.util.Collection;

/**
 * Classifica o que uma conta Microsoft pode jogar, a partir das informações oficiais da Mojang:
 * o perfil Java (GET /minecraft/profile) e os direitos (GET /entitlements/mcstore).
 *
 * Regras:
 *  - Perfil Java existente → jogo completo (só contas com licença têm perfil).
 *  - Direito "product_minecraft"/"game_minecraft" (Java) ou Game Pass PC/Ultimate → jogo completo,
 *    mas sem perfil ainda: é preciso criar o nome de jogador no site oficial.
 *  - Somente direitos Bedrock → NÃO é licença do Java: a conta usa a Demo oficial.
 *  - Nada → Demo oficial (é assim que o launcher oficial também se comporta).
 * Nunca transforma uma conta sem licença em conta com licença.
 */
public final class JcxOwnership {
    private JcxOwnership() {}

    public enum Kind {
        /** Licença Java com perfil: jogo completo. */
        JAVA_FULL,
        /** Licença Java (compra ou Game Pass) sem nome de jogador criado ainda. */
        JAVA_NO_PROFILE,
        /** Só Minecraft Bedrock: Java Edition roda como Demo oficial. */
        BEDROCK_ONLY_DEMO,
        /** Sem licença: Demo oficial. */
        DEMO
    }

    public static final class Result {
        public Kind kind;
        public boolean javaEntitlement, gamePass, bedrockEntitlement, hasProfile;

        public boolean isDemo() { return kind == Kind.BEDROCK_ONLY_DEMO || kind == Kind.DEMO; }
    }

    public static Result classify(Collection<String> entitlements, boolean hasJavaProfile) {
        Result r = new Result();
        r.hasProfile = hasJavaProfile;
        if (entitlements != null) for (String e : entitlements) {
            if (e == null) continue;
            switch (e) {
                case "product_minecraft":
                case "game_minecraft":
                    r.javaEntitlement = true; break;
                case "product_game_pass_pc":
                case "product_game_pass_ultimate":
                    r.gamePass = true; break;
                case "product_minecraft_bedrock":
                case "game_minecraft_bedrock":
                    r.bedrockEntitlement = true; break;
                default: break;
            }
        }
        if (hasJavaProfile) r.kind = Kind.JAVA_FULL;
        else if (r.javaEntitlement || r.gamePass) r.kind = Kind.JAVA_NO_PROFILE;
        else if (r.bedrockEntitlement) r.kind = Kind.BEDROCK_ONLY_DEMO;
        else r.kind = Kind.DEMO;
        return r;
    }

    public static String describe(Kind k) {
        switch (k) {
            case JAVA_FULL: return "Minecraft: Java Edition (jogo completo)";
            case JAVA_NO_PROFILE: return "Licença Java encontrada, mas sem nome de jogador";
            case BEDROCK_ONLY_DEMO: return "Só Bedrock nesta conta → Java roda como Demo oficial";
            default: return "Sem licença Java → Demo oficial";
        }
    }
}
