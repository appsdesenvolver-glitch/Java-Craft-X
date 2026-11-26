# Roadmap e registro de implementação

## v1.0.0
- Motor de execução integrado (Amethyst, LGPL-3.0) com Minecraft rodando dentro do app.
- Identidade JAVA CRAFT X, pacote próprio, CI que publica APKs, documentação PT-BR.

## v1.1.0 — Ultimate Edition
- X-PERFORMANCE (monitor + ECO / BALANCED / HIGH PERFORMANCE / CUSTOM, reversível).
- X-TOUCH (5 layouts próprios, sensibilidade, escala, toque longo, gestos) e menu rápido in-game (X-MONITOR, troca de layout).
- Mod Center, Shader Lab e Resource Packs integrados ao Modrinth, com dependências, conflitos, ativar/desativar e atualização.
- Download Engine (HTTPS, retomada, repetição, cancelamento, hash, espaço).
- World Manager (NBT, backup, restauração/importação segura, duplicar, renomear, reparar, excluir).
- Crash Analyzer (16 regras com evidência, etapa estimada, relatório exportável).
- Modo convidado honesto; testes unitários no CI.

## Ainda não implementado (próximos passos)
1. Medição de FPS fora do jogo (hoje: F3 dentro do jogo).
2. Pausar/retomar manual na fila de downloads (hoje: cancelar + retomada automática na próxima tentativa).
3. Exportar perfil de mods como `.mrpack` e backup/restauração de instalações inteiras.
4. Avatar personalizado para perfis locais.
5. Testes em aparelhos reais e preenchimento da matriz de compatibilidade com resultados medidos.
6. Telas do motor herdadas (editor de perfil, configurações) ainda com o visual do Amethyst recolorido; migrar para os componentes JCX.
