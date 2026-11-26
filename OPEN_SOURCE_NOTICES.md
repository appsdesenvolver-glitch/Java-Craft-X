# OPEN SOURCE NOTICES — JAVA CRAFT X

JAVA CRAFT X — criado por **KAIKODEV**. Distribuído sob GNU LGPL-3.0 (arquivo `LICENSE`).

Este arquivo registra a auditoria de licenças pedida para o JAVA CRAFT X ENGINE: o que foi
reutilizado, de onde veio, sob qual licença, e o que foi avaliado e **não** reutilizado.
Situação verificada em 26/11/2026.

## 1. Componentes incluídos no APK

| Componente | Origem | Licença | Como entra no JAVA CRAFT X |
|---|---|---|---|
| Amethyst Android | github.com/AngelAuraMC/Amethyst-Android (commit `9423d60`) | LGPL-3.0 | Base do motor (runtime, LWJGL, entrada, instaladores). Código-fonte completo e modificações neste repositório |
| PojavLauncher | github.com/PojavLauncherTeam/PojavLauncher | LGPL-3.0 | Ancestral do Amethyst; o código herdado mantém os pacotes `net.kdt.pojavlaunch` e os créditos originais. O projeto foi arquivado em 23/09/2025 e o Amethyst é o sucessor indicado pelos próprios autores |
| MobileGlues | github.com/MobileGL-Dev/MobileGlues (binário `MobileGlues-release.aar`, já trazido pelo Amethyst) | LGPL-2.1 | Renderizador "MobileGlues" (OpenGL sobre OpenGL ES 3.x). Não foi modificado; código-fonte no repositório original |
| GL4ES / Krypton Wrapper (NG-GL4ES) | github.com/ptitSeb/gl4es • github.com/BZLZHH/NG-GL4ES | MIT | Renderizador "Krypton". Não foi modificado |
| Mesa 3D / Zink, virglrenderer, libepoxy | freedesktop.org (forks AngelAuraMC) | MIT | Renderizador Zink (Vulkan) |
| OpenJDK 8/17/21/25 para Android | AngelAuraMC/angelauramc-openjdk-build (8 embutido; demais baixados na hora) | GPLv2 + Classpath Exception | Runtime Java |
| LWJGL 3 / LWJGLX | AngelAuraMC/lwjgl3 | BSD-3 / sem licença declarada (LWJGLX) | Bibliotecas do jogo |
| OpenAL Soft (+ oboe, pffft) | kcat/openal-soft (binário `openal-soft-release.aar`) | Conforme o README do Amethyst: GPLv2 / Apache-2.0 / ARR | Áudio do jogo |
| SDL3, sdl2-compat | libsdl-org | zlib | Gamepads e entrada |
| ANGLE | chromium.googlesource.com/angle | BSD-style (ver `assets/licenses/ANGLE_LICENSE`) | Backend GL opcional |
| bhook / bytehook | bytedance | MIT | Captura de código de saída |
| Bibliotecas AndroidX | Google | Apache-2.0 | Interface |

A lista completa herdada, com links, está em `docs/UPSTREAM_README.md` (seção *Credits & Dependencies*)
e o texto da licença do ANGLE em `app_pojavlauncher/src/main/assets/licenses/`. Os demais textos estão nos repositórios de origem linkados.

**Código próprio do JAVA CRAFT X** (pacote `net.kdt.pojavlaunch.jcx`, layouts X-TOUCH, telas, ícones,
logo, workflow e documentação): escrito para este projeto, sem cópia de outros launchers, sob LGPL-3.0.

Dependência só de teste: `junit:junit:4.13.2` (EPL-1.0) e `org.json:json:20231013` (domínio público / JSON License) — não vão para o APK.

## 2. Projetos avaliados e NÃO reutilizados

| Projeto | Licença | Situação | Decisão |
|---|---|---|---|
| OnyxLauncher (studio-basecode) | LGPL-3.0 | Derivado do PojavLauncher; 11 commits, 10 estrelas | Nenhum código copiado: o motor equivalente já vem do Amethyst (mais atual) e a interface do JAVA CRAFT X é própria |
| Nuvyra Launcher (Walksys) | GPL-3.0 | 13 commits, 0 estrelas; não informa qual motor gráfico usa | Não reutilizado: maturidade baixa e GPL-3.0 obrigaria relicenciar o app inteiro |
| Millida launcher-mobile | GPL-3.0-only | 1 commit; exige Android 8+, 64-bit, 4 GB+ RAM; oferece jogar sem licença | Não reutilizado (ver `docs/ARQUITETURA.md`) |
| MobileGlues-release | LGPL-2.1 (declarado no repositório) | Repositório de releases/documentação; fonte em MobileGL-Dev/MobileGlues | Já incluído via Amethyst; nenhum binário extra adicionado |

## 3. Minecraft

Minecraft é marca da Mojang Synergies AB / Microsoft. O JAVA CRAFT X não distribui nenhum arquivo do jogo:
metadados, client JAR, bibliotecas e assets são baixados dos servidores oficiais da Mojang no aparelho
do usuário e conferidos por hash. Projeto não oficial, sem vínculo com Mojang ou Microsoft.
