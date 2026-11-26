# Relatório técnico — v1.4.0 (autenticação, convidado, Demo oficial)

Investigação feita no código do JAVA CRAFT X em 26/11/2026, antes de qualquer mudança. Nada foi apagado
nem substituído por inteiro: as correções foram feitas nos arquivos existentes.

## Arquitetura (resumo)

- Motor: fork do **Amethyst Android** (LGPL-3.0), sucessor do PojavLauncher (arquivado). JVM ARM, LWJGL3,
  renderizadores Krypton/NG-GL4ES, MobileGlues, Zink e LTW, OpenAL. O jogo roda no processo interno `:game`
  do próprio APK (ver [ARQUITETURA.md](ARQUITETURA.md)).
- Login: `MicrosoftLoginFragment` (página oficial `login.live.com` numa WebView) → `MicrosoftBackgroundLogin`
  (Microsoft → Xbox Live → XSTS → Minecraft Services → perfil → licença) → `MinecraftAccount` (JSON por conta)
  → `mcAccountSpinner` (seleção e renovação).
- Módulos JAVA CRAFT X em `net.kdt.pojavlaunch.jcx`.

## Problemas encontrados e correções

| # | Problema encontrado | Efeito para o usuário | Correção |
|---|---|---|---|
| 1 | A tela de login **apagava todos os cookies da Microsoft a cada abertura** (`removeAllCookies`) | Toda tentativa pedia e-mail, senha e códigos de novo | A sessão da página oficial é mantida. Só "Entrar com outra conta" limpa |
| 2 | Uma falha depois da etapa Microsoft (ex.: falta de perfil Xbox) **descartava a autorização** | Era preciso refazer o login inteiro | A autorização fica guardada (cifrada) e o diálogo oferece **Tentar de novo**, sem senha |
| 3 | Erros mostrados como texto genérico (`RuntimeException(responseMessage)`) | Mensagens como "Bad Request", sem saber o que fazer | `JcxAuthError`: etapa (1/6…6/6), HTTP/XErr, descrição, causa provável, ação e link oficial |
| 4 | **Tokens e o código de autorização eram escritos no log** (`Log.i(... access token ...)`) | Risco ao compartilhar logs | Removidos; todo texto técnico passa por `JcxAuthLog.sanitize` |
| 5 | Conexões **sem tempo limite** | Login podia ficar travado para sempre em rede ruim | Tempo limite de conexão 15 s e leitura 25 s |
| 6 | Direitos **só Bedrock** eram contados como posse do Java | Conta com Bedrock (ex.: comprado no celular) caía no erro "nome de usuário não definido" e **nunca chegava à Demo** | `JcxOwnership`: só `product_minecraft`/`game_minecraft` (ou Game Pass PC/Ultimate) contam como Java; só Bedrock → **Demo oficial** |
| 7 | Game Pass sem perfil não era reconhecido (TODO no código) | — | Reconhecido como licença Java sem nome de jogador, com link para criar o nome |
| 8 | Tokens salvos em texto puro no JSON da conta | — | Cifrados com **Android Keystore** (AES-256-GCM). Contas antigas são migradas sem novo login. Android 5/6.0: mantém o armazenamento privado do app |
| 9 | Dois logins iguais podiam rodar ao mesmo tempo (seleção + recarga da lista) | Renovações duplicadas | Login idêntico em andamento é ignorado |
| 10 | Entrar de novo na mesma conta era ignorado sem selecioná-la | Parecia que "não fazia nada" | A conta existente é selecionada (sem duplicar) |
| 11 | Validade do token fixada em 24 h | — | Usa o `expires_in` oficial, com margem de 5 min |
| 12 | **Modo convidado** só mostrava um aviso e voltava; ao tocar JOGAR aparecia um toast e a tela de login reabria | Parecia quebrado (vai e volta) | O convidado é um estado real (Home mostra "Modo convidado"); JOGAR explica e oferece **Entrar** ou **Agora não**; todas as abas continuam acessíveis |
| 13 | A Demo não tinha nenhuma indicação visual | O usuário não sabia que existia nem quando estava ativa | Home: "DEMO OFICIAL"; botão **JOGAR DEMO**; limites oficiais mostrados na 1ª vez; Central de Contas com **Jogar a Demo** |
| 14 | Mensagem "precisa de conta Xbox" sem explicar que é grátis | O usuário achava que precisava pagar | Explica que o perfil Xbox é **gratuito**, abre a página oficial de criação (`start.ui.xboxlive.com/CreateAccount`, vinda da própria resposta da Microsoft) e permite tentar de novo |

## Conta Microsoft × Perfil Xbox × Assinatura × Licença × Demo

| Item | O que é | Custa? | Necessário para |
|---|---|---|---|
| Conta Microsoft | E-mail Outlook/Hotmail/Live | Grátis | Tudo (login) |
| Perfil Xbox (gamertag) | Identidade Xbox ligada à conta | **Grátis** | Login do Minecraft Java (etapa XSTS). Sem ele: XErr 2148916233 |
| Assinatura Xbox / Game Pass | Assinatura mensal | Pago, opcional | Só para jogar o Minecraft pelo Game Pass em vez de comprar |
| Licença Minecraft Java | Compra do *Java & Bedrock Edition for PC* (ou Game Pass PC) | Pago | Jogo completo, sem limite de tempo |
| Demo oficial | Liberada pela Mojang para contas sem a licença | Grátis | ~100 min / 5 dias de jogo, sobrevivência, sem servidores online |

## Por que a Demo "não aparecia"

1. Contas com **Bedrock** eram tratadas como donas do Java e travavam no erro de nome de usuário (item 6).
2. Contas **sem perfil Xbox** paravam na etapa XSTS, antes de a licença ser verificada (item 14). Com o perfil gratuito
   criado, o login continua e a Demo é atribuída.
3. Quando a Demo funcionava, nada na interface indicava isso (item 13).

Quem tem a licença do Java **sempre recebe o jogo completo**: a Mojang não oferece Demo para contas que possuem o
jogo, e o launcher não finge o contrário. Para ver a Demo, use uma conta Microsoft sem o Java.

## Executáveis enviados

Ver [EXE_AUDIT.md](EXE_AUDIT.md). Resumo: os dois `MinecraftInstaller` são o instalador oficial da Microsoft
para Windows (sem o jogo dentro); o TLauncher é um launcher não oficial para Windows. Nenhum pode rodar no Android e
nenhum foi integrado.

## Fontes pesquisadas

- Mojang/Microsoft: minecraft.net/free-trial, central de ajuda "Play the Minecraft: Java Edition Demo", `version_manifest_v2`.
- Esquema de autenticação documentado pela comunidade (Minecraft Wiki, projeto wiki.vg): códigos XErr e `entitlements/mcstore`.
- Amethyst Android (issue #160: XErr 2148916233 com `Redirect` para criação de conta Xbox).

## Limitações honestas

- Nada disto foi testado num aparelho; os testes de login em aparelho continuam pendentes ([RELATORIO_TESTES.md](RELATORIO_TESTES.md)).
- O launcher não consegue criar o perfil Xbox nem o nome de jogador sozinho: abre a página oficial e continua depois.
- Restrições de país, de idade e de conta infantil são da Microsoft e não podem ser contornadas.
- O client ID de login é o público usado pelo Amethyst/PojavLauncher; se a Microsoft/Mojang mudar regras para ele,
  o login pode parar até haver atualização.
