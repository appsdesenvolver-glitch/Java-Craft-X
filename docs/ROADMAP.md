# Roadmap e registro de implementação

## Feito (v1.0.0)

- Motor de execução integrado (Amethyst, LGPL-3.0) com Minecraft rodando dentro do app.
- Identidade JAVA CRAFT X: paleta, logo, ícone adaptativo, botão JOGAR, cabeçalho do dashboard, nome em todas as traduções.
- Pacote próprio `com.javacraftx.launcher`; assinatura de release configurável por segredos.
- CI que gera APK release + debug e publica em Releases.
- Documentação PT-BR (README, autenticação, compatibilidade, testes, NOTICE).

## Próximos módulos (não implementados ainda)

Cada item abaixo precisa ser desenvolvido em Java dentro de `app_pojavlauncher` e testado num aparelho:

1. **X-Boost** — perfis ECO / BALANCED / VISUAL / CUSTOM que ajustam heap (por RAM detectada via
   `ActivityManager.MemoryInfo`), escala de resolução e `options.txt` (render/simulation distance, partículas).
2. **World Manager** — listar `saves/` por perfil, ler `level.dat` (NBT) para versão, backup/restore em ZIP
   com proteção contra path traversal, duplicar, renomear, excluir com confirmação.
3. **Crash Analyzer** — ler `latestlog.txt`/crash-reports, regras para OutOfMemoryError, mod ausente,
   GL errors, mixin conflicts; mostrar "possível causa" (nunca diagnóstico definitivo) e exportar relatório.
4. **Shader Lab** — aba própria com busca Modrinth (`project_type=shader`), avisos por renderizador.
5. **Device Monitor** — RAM, armazenamento, temperatura (`BatteryManager`) e tempo de inicialização.
6. Dashboard estendido — cartões de conta, última versão, estado do Java/renderizador, armazenamento.
