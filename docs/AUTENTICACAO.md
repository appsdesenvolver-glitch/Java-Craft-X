# Autenticação Microsoft

## Como funciona

1. Em **Contas → Adicionar → Microsoft**, o app abre a página oficial `login.live.com` numa WebView do sistema.
   O usuário digita e-mail/senha **no site da Microsoft**; o launcher nunca vê nem guarda a senha.
2. A Microsoft devolve um código OAuth, trocado por tokens.
3. Cadeia oficial: Microsoft OAuth → Xbox Live → XSTS → Minecraft Services (`login_with_xbox`).
4. O app consulta o perfil e `entitlements/mcstore`: com licença Java → jogo completo; sem licença (ou só Bedrock) →
   **Demo oficial**; licença sem nome de jogador → pede para criar o nome em minecraft.net.
5. Os tokens ficam cifrados no **Android Keystore** (AES-256-GCM) e são renovados automaticamente, sem senha.
6. A sessão da página oficial da Microsoft fica salva no app: se você marcar "Manter conectado", as próximas
   autorizações pedem só um toque. **Entrar com outra conta** limpa essa sessão.
7. Se o login falhar depois da autorização (ex.: falta o perfil Xbox), a autorização fica guardada e o diálogo de erro
   oferece **Tentar de novo** sem pedir a senha. A Central de Contas mostra o login pendente e o último diagnóstico
   (sem tokens), com botão para copiar.

## Client ID

O código usa o client ID público do Minecraft Launcher (`00000000402b5328`), o mesmo usado pelo Amethyst/PojavLauncher.
**Nenhum cadastro é necessário** para compilar e usar.

Se quiser um client ID próprio (Azure):

1. Portal Azure → *App registrations* → *New registration*, contas pessoais Microsoft.
2. Plataforma *Mobile and desktop*, redirect URI `https://login.live.com/oauth20_desktop.srf`.
3. Peça acesso à API do Minecraft no formulário oficial da Mojang (aplicativos novos precisam de aprovação;
   sem isso `login_with_xbox` retorna 403).
4. Troque o ID em `MicrosoftLoginFragment.java` e `MicrosoftBackgroundLogin.java`.

## Sair

Contas → segurar a conta → excluir. Os tokens são apagados do aparelho.
Para revogar totalmente: https://account.live.com/consent/Manage

## Erros comuns

Cada erro mostra a etapa (1/6 a 6/6), o código HTTP/XErr, a causa provável, o que fazer e, quando existe, o link oficial.

| Etapa / código | Causa | O que fazer |
|---|---|---|
| 3/6 XErr 2148916233 | Conta sem **perfil Xbox** (gratuito) | Botão "Criar perfil Xbox" → criar gamertag → "Tentar de novo" |
| 3/6 XErr 2148916235 | Xbox Live indisponível no país da conta | Conferir o país em account.microsoft.com |
| 3/6 XErr 2148916236/7 | Verificação de idade | Verificar em account.xbox.com |
| 3/6 XErr 2148916238 | Conta de menor fora de uma Família Microsoft | Um adulto adiciona em account.microsoft.com/family |
| 1/6 HTTP 400 invalid_grant | Autorização salva expirou ou foi revogada | "Entrar de novo" (com "Manter conectado", sem senha) |
| 5/6 "Crie seu nome de jogador" | Licença Java/Game Pass sem nome criado | "Criar nome de jogador" em minecraft.net → "Tentar de novo" |
| HTTP 429 | Muitas tentativas seguidas | Esperar alguns minutos |
| Sem conexão / tempo esgotado | Internet, DNS, data/hora errada | Conferir rede e data/hora automática |
