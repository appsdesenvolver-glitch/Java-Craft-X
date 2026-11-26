# Relatório de testes — JAVA CRAFT X

Este relatório só registra o que foi realmente executado. Atualizado em 26/11/2026.

## Ambiente

O código foi escrito num ambiente Linux **sem Android SDK, sem emulador e sem aparelho Android**.
A compilação acontece no GitHub Actions. Por isso há três níveis de verificação abaixo:
build no CI, testes unitários (JVM) e testes em aparelho (**não executados**).

## 1. Build no GitHub Actions

| Build | Commit | Conteúdo | Resultado |
|---|---|---|---|
| 1 | `805a82e` | v1.0.0 — identidade JCX | ✅ release + debug |
| 2 | `031327d` | documentação | ✅ |
| 3 | `99b1030` | v1.1.0 Ultimate — módulos JCX | ✅ release (156.844.937 bytes) + debug (168.750.390 bytes) + testes |

Todas as etapas passaram: checkout com submódulos, JRE 8, assembleRelease, assembleDebug,
testDebugUnitTest, checksums, release.

## 2. Testes unitários (JUnit, `app_pojavlauncher/src/test`)

12 testes em `JcxCoreTest`, executados no CI a cada build (resultado publicado nas notas de cada release).
9 deles (os que não dependem de classes Android) também foram executados localmente: **OK (9 tests)**.

| Teste | Verifica |
|---|---|
| nbtReadsLevelInfo | Leitura de LevelName, versão, último acesso e modo de jogo do level.dat |
| nbtRenameKeepsOtherDataAndBackup | Renomear mantém todos os outros dados NBT e cria `level.dat_jcxbak` |
| nbtRejectsGarbage | level.dat inválido gera erro (marcado como "possivelmente corrompido") |
| backupAndRestoreWorld | Backup ZIP → detecção da raiz do mundo → restauração completa |
| zipSlipIsBlocked | Entrada `../../` no ZIP é bloqueada e nada é escrito fora do destino |
| zipWithoutWorldHasNoPrefix | ZIP que não é mundo é recusado |
| hashMatchesKnownSha1 | SHA-1 correto (vetor "abc") |
| crashAnalyzerFindsKnownCauses | Detecta OutOfMemory, Mixin e versão de Java, com linha correta e etapa |
| crashAnalyzerDoesNotInventCauses | Log sem erro não gera "causa" |
| xperformanceHeapLimitsAreSafe | Heap recomendado nunca passa do máximo seguro |
| mcVersionGuessFromLoaderIds | Versão do Minecraft extraída de IDs de Fabric/Forge |
| safeFileNameStripsTraversal | Nomes vindos da internet não conseguem sair da pasta |

## 3. Verificações estáticas

- XML de layouts, drawables e 60+ arquivos de strings validados por parser.
- Layouts X-TOUCH renderizados numa simulação de tela 1600×720 (Moto E13): **nenhum botão sobreposto**.
- Endpoints usados: somente Microsoft/Xbox/Mojang oficiais (motor) e `api.modrinth.com` / `cdn.modrinth.com`.

## 4. Testes em aparelho — NÃO EXECUTADOS

Instalação do APK, abertura, autenticação, modo convidado, runtime, instalação de versões,
inicialização e renderização do Minecraft, controles X-TOUCH, menu rápido, mods, shaders,
multiplayer autenticado, desempenho (FPS, tempo de inicialização, memória), encerramento,
reabertura e recuperação de falhas **não foram testados** — exigem um aparelho Android físico e
uma conta Microsoft com o Minecraft. Nenhum número de FPS ou tempo foi inventado.

### Roteiro sugerido (Moto E13 ou outro)

1. Instalar `JavaCraftX-release.apk` e abrir. Tocar em **Modo convidado** e navegar pelas abas.
2. Adicionar conta Microsoft e confirmar nome/skin.
3. Criar perfil **1.16.5** → X-PERFORMANCE → **ECO** → JOGAR. Anotar tempo até o menu.
4. Em jogo: X-TOUCH (layout Survival), botão **X MENU → X-MONITOR**, trocar para PVP.
5. Sair, reabrir o app, jogar de novo.
6. Perfil **Fabric 1.20.1** → Mod Center → instalar **Sodium** (dependências automáticas) → JOGAR.
7. Shader Lab com Iris instalado e renderizador MobileGlues → instalar um shader "potato/low".
8. World Manager → Backup → Restaurar → Renomear.
9. Forçar heap alto (CUSTOM) até falhar e abrir o **Crash Analyzer**.
