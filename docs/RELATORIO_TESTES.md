# Relatório de testes — JAVA CRAFT X 1.0.0

Data: 26/11/2026. Este relatório só registra o que foi realmente verificado.

## Ambiente de desenvolvimento

O código foi preparado num ambiente Linux **sem Android SDK e sem acesso à rede de pacotes** (Google Maven,
Maven Central, Gradle). Por isso **nenhuma compilação local foi possível**: a compilação acontece no GitHub
Actions. Também **não havia aparelho Android ou emulador** disponível.

## O que foi verificado

| Item | Resultado |
|---|---|
| Base Amethyst no commit `9423d60` compila no CI oficial | ✅ (execução "Android CI" do upstream concluída com sucesso em 24/11/2026) |
| Todos os XML alterados (layouts, drawables, cores, 60+ arquivos de strings) são XML válidos | ✅ (parser) |
| Ícones e logo gerados nas 5 densidades | ✅ (inspeção visual) |
| Nenhuma referência restante ao pacote `org.angelauramc.amethyst` | ✅ (busca no código) |
| Fluxo de login usa só endpoints oficiais Microsoft/Xbox/Mojang | ✅ (revisão de código) |
| Build do APK no GitHub Actions deste repositório | ver aba **Actions** — resultado da 1ª execução registrado abaixo |

## Testes obrigatórios da especificação (1–16)

Instalação do APK, abertura, autenticação, instalação de runtime, instalação de versão, verificação de arquivos,
inicialização real do jogo, renderização, controles, encerramento, reabertura, mods, shaders, versões,
recuperação de falhas e backup de mundos: **NÃO EXECUTADOS** — exigem um aparelho Android físico com uma conta
Microsoft que possua o Minecraft. Essas funções vêm do motor Amethyst, que é usado em produção, mas o
comportamento no seu aparelho só pode ser confirmado instalando o APK.

### Roteiro sugerido (Moto E13 ou outro)

1. Instalar `JavaCraftX-release.apk`; abrir; aceitar armazenamento.
2. Adicionar conta Microsoft; confirmar que o nome/skin aparecem.
3. Criar perfil **1.16.5** (Java 8 embutido) → JOGAR → anotar tempo até o menu.
4. Memória ~800 MB, renderizador Krypton, render distance 4. Entrar num mundo e testar controles.
5. Sair do jogo, reabrir o app, jogar de novo.
6. Perfil **Fabric 1.20.1** → instalar **Sodium** pela central Modrinth → JOGAR (baixará Java 17).
7. Forçar erro (heap 4 GB num aparelho de 3 GB) e verificar a tela de log.

## Resultado do CI

_(preenchido após a primeira execução no GitHub Actions)_
