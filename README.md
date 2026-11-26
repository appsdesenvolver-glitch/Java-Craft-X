<p align="center"><img src="app_pojavlauncher/src/main/res/drawable-nodpi/jcx_logo.png" height="72" alt="JAVA CRAFT X"></p>
<p align="center"><b>Advanced Java Gaming Environment</b><br>Launcher Android que roda <b>Minecraft: Java Edition dentro do próprio app</b>.</p>

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

| Área | Status | Observação |
|---|---|---|
| Executar Minecraft Java dentro do app | ✅ motor Amethyst | JVM ARM + LWJGL3 + renderizadores Krypton (NG-GL4ES), MobileGlues, Zink (Vulkan) e LTW, conforme a GPU |
| Login Microsoft oficial | ✅ | OAuth no site oficial `login.live.com` (WebView do sistema), tokens Xbox/Minecraft, renovação automática. Senha nunca passa pelo launcher |
| Conta offline/local | ✅ | Só para servidores/mundos que permitam |
| Versões (releases, snapshots, antigas) | ✅ | Manifesto oficial da Mojang, verificação SHA-1, download paralelo, reparo |
| Java 8 / 17 / 21 / 25 | ✅ | Java 8 embutido no APK; 17/21/25 baixados automaticamente na 1ª vez que uma versão nova é iniciada |
| Fabric / Quilt / Forge / NeoForge / OptiFine | ✅ | Instaladores integrados |
| Central de mods e modpacks (Modrinth) | ✅ | Busca, versões, instalação, modpacks `.mrpack` |
| CurseForge | ⚙️ | Precisa da chave da API no segredo `CURSEFORGE_API_KEY`; sem ela fica desativado |
| Shaders | ⚠️ parcial | Instalados como mods (Iris/OptiFine). Funcionam só com renderizadores/GPUs que suportem; não há suporte universal |
| Editor de controles touch | ✅ | Botões, analógico, opacidade, tamanho, perfis, import/export |
| Teclado, mouse e gamepad | ✅ | Inclui remapeamento de controle |
| Memória (heap Java) | ✅ | Slider limitado à RAM do aparelho |
| Renderizador, resolução, argumentos JVM | ✅ | Por perfil ou global |
| Perfis isolados (pasta por instalação) | ✅ | |
| Logs e compartilhamento de log | ✅ | Tela de erro com log após falha |
| Gerenciador de arquivos/mundos | ✅ básico | Via provedor de documentos do Android (botão "Abrir pasta do jogo") |
| Dashboard com identidade JAVA CRAFT X | ✅ | Paleta preto/roxo/azul neon/ciano, logo, ícone e botão JOGAR novos |
| X-Boost (perfis ECO/BALANCED/VISUAL), Shader Lab dedicado, World Manager com backup, Crash Analyzer com diagnóstico, monitor de temperatura | ❌ ainda não | Planejados — ver [docs/ROADMAP.md](docs/ROADMAP.md) |

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
- [README original do Amethyst](docs/UPSTREAM_README.md) (créditos completos)

## Licença

GNU LGPL-3.0 — ver [LICENSE](LICENSE). Minecraft é marca da Mojang/Microsoft; o JAVA CRAFT X **não distribui arquivos do jogo** e exige uma conta Microsoft que possua o Minecraft: Java Edition para jogar online. Projeto não oficial, sem vínculo com Mojang ou Microsoft.
