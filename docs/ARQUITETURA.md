# Arquitetura, decisões e licenças

## Visão geral

```
JAVA CRAFT X (APK com.javacraftx.launcher)
├── Interface do launcher (Android Views, Java)
│   ├── Dashboard JCX (logo, módulos, botão JOGAR)
│   └── net.kdt.pojavlaunch.jcx.*  ← módulos próprios do JAVA CRAFT X
│       ├── XPerformance / XPerformanceFragment   (X-PERFORMANCE)
│       ├── XTouchFragment + assets/xtouch/*.json  (X-TOUCH)
│       ├── XMonitor                               (menu rápido in-game)
│       ├── Modrinth / ContentIndex / ContentLabFragment (Mod Center, Shader Lab, Resource Packs)
│       ├── JcxDownloader                          (Download Engine)
│       ├── Nbt / WorldManagerFragment             (World Manager)
│       ├── CrashAnalyzer / CrashAnalyzerFragment  (Crash Analyzer)
│       └── JcxUi / JcxPaths / JcxFiles            (componentes, caminhos, arquivos seguros)
├── Motor de execução (Amethyst, LGPL-3.0) — mantido separado, sem alterações de lógica
│   ├── Runtime: OpenJDK 8/17/21/25 para Android, JNI, carregamento de natives
│   ├── Gráficos: LWJGL3, Krypton (NG-GL4ES), MobileGlues, Mesa/Zink (Vulkan), LTW, EGL
│   ├── Entrada: controles customizáveis, gamepad (SDL3), teclado/mouse físicos
│   └── Instaladores: versões Mojang, Fabric, Quilt, Forge, NeoForge, OptiFine, modpacks
└── Dados por perfil: <armazenamento do app>/.minecraft/<pasta do perfil>/{mods, shaderpacks, resourcepacks, saves, options.txt}
```

Os módulos JCX conversam com o motor apenas por pontos públicos: preferências
(`LauncherPreferences`), perfis (`LauncherProfiles`/`MinecraftProfile`), a pasta do jogo e os
arquivos de layout de controle. O motor não foi reescrito nem simulado.

## Por que Flutter não foi usado

A especificação sugere Flutter para a interface. A interface atual é nativa (Android Views)
porque o motor de execução, a superfície OpenGL do jogo e os controles in-game são Android
nativos. Colocar Flutter por cima acrescentaria um segundo runtime de UI (~10–15 MB e memória
extra), o que pesa justamente em aparelhos como o Moto E13, sem ganho funcional.
Essa decisão pode ser revista se houver uma razão de produto.

## Avaliação de motores

| Opção | Licença | Situação (nov/2026) | Decisão |
|---|---|---|---|
| Amethyst Android (AngelAuraMC) | LGPL-3.0 | Ativo; CI oficial verde no commit usado (`9423d60`) | **Base do motor** |
| Millida launcher-mobile | GPL-3.0-only | 1 commit, 0 estrelas; Android 8+, 64-bit, 4 GB+ RAM, OpenGL ES 3.0; runtime e renderizadores baixados como binários prontos; oferece "jogar sem licença" em servidores próprios | Não integrado: maturidade baixa, exige mais RAM do que o Moto E13 tem e o modo sem licença conflita com a regra de não burlar licenciamento. Misturar os dois projetos também obrigaria a relicenciar tudo como GPL |

## Licenças e obrigações

- O código do JAVA CRAFT X é distribuído sob **LGPL-3.0** (`LICENSE`), com o código-fonte completo
  neste repositório público, avisos de autoria em `NOTICE.md` e licenças de terceiros em
  `app_pojavlauncher/src/main/assets/licenses/` e `docs/UPSTREAM_README.md`.
- Dependências novas adicionadas pelo JAVA CRAFT X: **nenhuma em tempo de execução** (os módulos
  JCX usam só o SDK Android, AndroidX já presente e `org.json`). Para testes: `junit:junit:4.13.2` (EPL-1.0).
- API do Modrinth: pública, sem chave, usada com User-Agent identificado conforme a documentação
  oficial. Downloads só de `*.modrinth.com` via HTTPS.
- Nenhum arquivo do Minecraft é distribuído. Tudo vem dos servidores da Mojang no aparelho do usuário.

## Segurança

- HTTPS obrigatório e lista de hosts permitidos nos downloads de conteúdo; redirecionamento para HTTP é recusado.
- Hash SHA-512 (ou SHA-1) conferido antes de mover qualquer download para a pasta do jogo.
- ZIP: extração com checagem de caminho canônico (ZIP Slip), limite de 8 GB, restauração sempre em pasta nova.
- NBT: limites de profundidade e tamanho; regravação valida o arquivo antes de substituir e guarda cópia.
- Nomes de arquivo vindos da internet são sanitizados (sem `/`, `\`, `..`, caracteres reservados).
- Tokens Microsoft ficam no armazenamento privado do app (motor Amethyst); senhas nunca passam pelo launcher.
