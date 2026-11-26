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
| 4 | `e5e02ea` | v1.1.0 docs + notas de release | ✅ 12/12 testes |
| 5 | `9ad22bd` | v1.2.0 Next Generation | ✅ release (156.881.731 bytes) + debug (168.797.600 bytes) + 18/18 testes |
| 7 | `463169b` | v1.3.0 Sessões + World Save Protection | ✅ release (156.893.326 bytes) + debug (168.807.244 bytes) + **23/23 testes** |

Todas as etapas passaram: checkout com submódulos, JRE 8, assembleRelease, assembleDebug,
testDebugUnitTest, checksums, release.

## 2. Testes unitários (JUnit, `app_pojavlauncher/src/test`)

Build 5: **18 testes, 0 falhas, 0 erros** (`JcxCoreTest` 12 + `JcxEngineTest` 6).

`JcxEngineTest`: separação host:porta (incl. IPv6), VarInt do protocolo, leitura do status de servidor
(versão, jogadores, MOTD sem códigos de cor), servers.dat sem compressão (ida e volta), parse do
manifesto oficial e categorias (release, snapshot, pre-release, RC, beta, alpha), comparação de versões.

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

`WorldGuardTest` (proteção de mundos): detecção apenas dos mundos gravados na sessão, mundo corrompido
não sobrescreve backups, backup pulado por falta de espaço, backup restaurável, rotação que preserva
backups manuais.

## 3. Verificações estáticas

- XML de layouts, drawables e 60+ arquivos de strings validados por parser.
- Layouts X-TOUCH renderizados numa simulação de tela 1600×720 (Moto E13): **nenhum botão sobreposto**.
- Endpoints usados: somente Microsoft/Xbox/Mojang oficiais (motor) e `api.modrinth.com` / `cdn.modrinth.com`.

## 4. Testes em aparelho — NÃO EXECUTADOS

Critérios de aceitação da especificação (instalar versão legítima, runtime correto, jogo iniciar,
renderização, áudio, controles, criar mundo, mover, inventário, construir, salvar, voltar ao launcher,
registrar erros) e os testes de Version Hub, multiplayer e controles externos:
instalação do APK, abertura, autenticação, modo convidado, runtime, instalação de versões,
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
10. Version Hub → filtro Releases → 1.20.1 → **Instalar agora** (ver barra inferior) → **Criar perfil**.
11. Multiplayer → adicionar um servidor → **Verificar** → **Jogar**.
12. Conectar controle Bluetooth → X-TOUCH → conferir se aparece em "Dispositivos detectados".
13. No jogo: X MENU → tamanho dos botões, ocultar controles, captura de tela, voltar ao launcher.
14. Depois de cada versão testada: Version Hub → **Registrar resultado de teste** (preenche a matriz).
15. Jogar 5 minutos, "Salvar e sair", fechar o jogo → conferir a tela "Sessão encerrada", o backup automático e o cartão "Bem-vindo de volta".
16. Fechar o app à força durante a partida → reabrir → conferir o aviso de session.lock.
