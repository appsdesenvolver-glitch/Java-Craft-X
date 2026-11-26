<p align="center"><img src="app_pojavlauncher/src/main/res/drawable-nodpi/jcx_logo.png" height="72" alt="JAVA CRAFT X"></p>
<p align="center"><b>Advanced Java Gaming Environment</b> • by KAIKODEV<br>Launcher Android que roda <b>Minecraft: Java Edition dentro do próprio app</b>.</p>

---

## O que é

O JAVA CRAFT X é um launcher Android (ARM64 prioritário) que instala, autentica e **executa Minecraft Java Edition numa superfície OpenGL do próprio aplicativo**. Não abre outro launcher, não usa WebView e não faz streaming: a JVM ARM roda no próprio aparelho.

O motor de execução **não foi escrito do zero**. Ele vem do projeto **[Amethyst Android](https://github.com/AngelAuraMC/Amethyst-Android)** (sucessor do PojavLauncher), licenciado sob **GNU LGPL-3.0**. O JAVA CRAFT X é um fork desse motor com identidade, pacote, build e documentação próprios. Veja [NOTICE.md](NOTICE.md).

## Download do APK

Cada push na branch `main` compila o app no GitHub Actions e publica:

- **Releases** → `JavaCraftX-release.apk` (instale este) e `JavaCraftX-debug.apk`.
- **Actions → execução → Artifacts** → `JavaCraftX-APKs`.

Pacote: `com.javacraftx.launcher` (o debug é `com.javacraftx.launcher.debug` e instala ao lado).

## Funcionalidades (estado real)

Legenda: ✅ implementado no código • ⚠️ parcial/limitado • ⚙️ precisa de configuração externa.
"Implementado" não quer dizer "testado num aparelho" — ver [relatório de testes](docs/RELATORIO_TESTES.md).

| Módulo | Status | O que faz de verdade |
|---|---|---|
| Motor Java integrado | ✅ (Amethyst) | JVM ARM + LWJGL3 + renderizadores Krypton (NG-GL4ES), MobileGlues, Zink (Vulkan) e LTW; o jogo roda numa superfície do próprio app |
| Conta Microsoft | ✅ | OAuth oficial em `login.live.com`, Xbox/XSTS, verificação de posse do jogo, renovação de token, logout |
| **Modo convidado** | ✅ | Usa todo o launcher sem conta. Para jogar exige conta com licença; sem licença só a Demo oficial. Nada é desbloqueado sem licença |
| Perfil local | ✅ | Liberado só depois de existir uma conta Microsoft; não entra em servidores online |
| Versões (releases, snapshots, antigas) | ✅ | Manifesto oficial, SHA-1, downloads paralelos, perfis/pastas independentes, Java 8 embutido e 17/21/25 baixados automaticamente |
| Loaders | ✅ | Fabric, Quilt, Forge, NeoForge, OptiFine |
| **Version Hub** | ✅ | Catálogo dinâmico do manifesto oficial `version_manifest_v2` (sem lista fixa): latest release/snapshot, releases, snapshots, pre-releases, RCs, alpha/beta históricas e versões com loader instaladas. Filtros (todas, instaladas, não instaladas, releases, snapshots, históricas, favoritas, compatíveis, incompatíveis), busca, aviso de versões novas, detalhes conferidos por SHA-1 (Java, client JAR, assets, bibliotecas), instalar/verificar/reparar pelo motor, criar perfil com instância isolada, remover com aviso de perfis afetados |
| **Matriz de compatibilidade** | ✅ | COMPATIBLE / LIMITED / UNSUPPORTED só por teste registrado ou impossibilidade técnica (ex.: Java > 25); o resto é UNKNOWN com observações |
| **X-Engine** | ✅ | Capacidades do aparelho (arquitetura, Android, RAM, armazenamento, OpenGL ES, Vulkan) e recomendações; Renderer Manager por perfil com escolha automática, requisitos, indisponíveis marcados e diagnóstico da última falha gráfica; Runtime Manager (Java instalados, remover) |
| **Multiplayer** | ✅ | Lista de servidores do próprio jogo (`servers.dat`): adicionar, editar, remover, favorito no topo, copiar IP, status real (online, jogadores, versão, ping, MOTD) e atalho para jogar. Entrada no servidor com a autenticação normal do jogo |
| **Sessões de jogo** | ✅ | Início registrado quando o JOGAR realmente inicia o motor; fim detectado quando o processo interno do jogo termina. Tela de resumo: tempo da sessão, mundos gravados (com verificação do level.dat), aviso de fechamento sem salvar, possíveis erros, histórico das últimas 20 sessões |
| **World Save Protection** | ✅ | Backup automático ao fim de cada sessão (só de mundos íntegros, com checagem de espaço e rotação dos 5 mais recentes; backups manuais nunca são apagados), cartão "Bem-vindo de volta" com o último mundo que ainda existe |
| Demonstração oficial | ✅ (motor) | Contas Microsoft sem o jogo rodam a Demo oficial. O tempo do mundo de demo é exibido; **o limite da demo não é alterado** |
| **X-PERFORMANCE** | ✅ | Monitor (RAM, pressão de memória, temperatura, armazenamento) + modos ECO / BALANCED / HIGH PERFORMANCE / CUSTOM que ajustam heap (até o máximo seguro), resolução e `options.txt` (distância, simulação, FPS, VSync, partículas, gráficos, sombras, mipmap). Botão para restaurar o original |
| **X-TOUCH Studio** | ✅ | 7 layouts próprios (Survival, PVP, Creative, Parkour, Builder, Minimal, Custom); usar, duplicar, restaurar, importar, exportar, usar só num perfil; sensibilidade, escala, toque longo, gestos; editor visual (arrastar, redimensionar, opacidade, ação); detecção de controle Bluetooth/USB, teclado e mouse; mapeamento de gamepad |
| **Game HUD / menu rápido** | ✅ | Botão X MENU sem pausar o jogo: X-MONITOR, trocar layout, tamanho dos botões ao vivo, ocultar controles, captura de tela (F2), configurações rápidas (resolução, sensibilidade), editor, logs, voltar ao launcher (o jogo roda num processo interno separado) |
| **Mod Center** (Modrinth) | ✅ | Busca, categorias, ordenação, filtro por versão e loader, dependências obrigatórias instaladas junto, aviso de conflito, ativar/desativar, atualizar, remover |
| **Shader Lab** (Modrinth) | ⚠️ | Busca/instala/remove shaderpacks com avisos de requisito (Iris/OptiFine, renderizador). Funcionar depende da GPU — nunca é marcado como compatível |
| Resource Packs (Modrinth) | ✅ | Mesmo fluxo, com verificação de versão pelos metadados |
| Modpacks | ✅ (Amethyst) | Instalação de `.mrpack` do Modrinth em perfil isolado |
| **Download Engine** | ✅ | HTTPS obrigatório, retomada (Range), 4 tentativas, cancelar, progresso e velocidade reais, espaço livre, SHA-512/SHA-1 |
| **World Manager** | ✅ | Lista com ícone, nome real e versão (NBT), tamanho, data; backup ZIP; restaurar/importar (.zip) sempre em pasta nova com proteção ZIP Slip; duplicar; renomear (nome no jogo + pasta); reparar via `level.dat_old`; excluir com dupla confirmação |
| **Crash Analyzer** | ✅ | Lê `latestlog.txt`, `logs/latest.log` e o crash-report mais recente; 16 regras (memória, heap, Java, dependências, duplicados, Mixin, gráficos, shader, nativo, rede, armazenamento, arquivo corrompido, sessão…), sempre com a linha de evidência e marcadas como "possível causa"; relatório exportável |
| CurseForge | ⚙️ | Precisa do segredo `CURSEFORGE_API_KEY` |
| Interface | ✅ | Paleta preto/roxo/azul neon/ciano, logo, ícone, componentes próprios nas telas JCX |

Nada nesta tabela foi marcado como pronto sem existir no código.

## Desempenho em aparelhos fracos (ex.: Moto E13, 2 GB RAM)

- Use versões **1.16.5 ou anteriores** (Java 8, mais leves), ou 1.20+ com **Sodium** e render distance 4–6.
- Memória: **~700–900 MB** de heap em aparelhos com 2 GB; não passe de metade da RAM.
- Renderizador **Krypton (NG-GL4ES)** costuma ser o mais leve em GPUs antigas (PowerVR/Mali); para 1.21.5+ teste **MobileGlues**.
- Feche outros apps antes de jogar. Não há "ganho mágico de FPS": o limite é o hardware.

## Compilar

Pré-requisitos: JDK 21, Android SDK + NDK `27.3.13750724`, Gradle 9.6.1.

```bash
git clone --recursive https://github.com/appsdesenvolver-glitch/JAVA-CRAFT-X
cd JAVA-CRAFT-X
# Coloque o JRE 8 Android em app_pojavlauncher/src/main/assets/components/jre
# (artefato "jre8-pojav" de AngelAuraMC/angelauramc-openjdk-build)
./scripts/languagelist_updater.sh
gradle :app_pojavlauncher:assembleDebug
```

O jeito mais simples é o GitHub Actions (`.github/workflows/android-build.yml`), que faz tudo isso.

### Assinatura de release (opcional)

Sem segredos, o release é assinado com a chave de debug (instala normalmente, mas **atualizações futuras exigem a mesma chave**). Para uma chave própria, crie em *Settings → Secrets and variables → Actions*:

| Segredo | Conteúdo |
|---|---|
| `JCX_KEYSTORE_BASE64` | `base64 -w0 sua-chave.jks` |
| `JCX_KEYSTORE_PASSWORD` | senha da keystore/chave |
| `JCX_KEY_ALIAS` | alias da chave (padrão `javacraftx`) |

## Documentação

- [Autenticação Microsoft](docs/AUTENTICACAO.md)
- [Relatório de testes](docs/RELATORIO_TESTES.md)
- [Compatibilidade](docs/COMPATIBILIDADE.md)
- [Roadmap](docs/ROADMAP.md)
- [Arquitetura e licenças](docs/ARQUITETURA.md)
- [Auditoria de código aberto / terceiros](OPEN_SOURCE_NOTICES.md)
- [Auditoria dos EXE do Windows](docs/EXE_AUDIT.md)
- [README original do Amethyst](docs/UPSTREAM_README.md) (créditos completos)

## Licença

GNU LGPL-3.0 — ver [LICENSE](LICENSE). Minecraft é marca da Mojang/Microsoft; o JAVA CRAFT X **não distribui arquivos do jogo** e exige uma conta Microsoft que possua o Minecraft: Java Edition para jogar online. Projeto não oficial, sem vínculo com Mojang ou Microsoft.
