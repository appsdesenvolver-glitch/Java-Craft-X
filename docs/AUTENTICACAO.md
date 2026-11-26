# Autenticação Microsoft

## Como funciona

1. Em **Contas → Adicionar → Microsoft**, o app abre a página oficial `login.live.com` numa WebView do sistema.
   O usuário digita e-mail/senha **no site da Microsoft**; o launcher nunca vê nem guarda a senha.
2. A Microsoft devolve um código OAuth, trocado por tokens.
3. Cadeia oficial: Microsoft OAuth → Xbox Live → XSTS → Minecraft Services (`login_with_xbox`).
4. O app consulta `entitlements/mcstore` e o perfil para confirmar que a conta **possui** Minecraft: Java Edition.
5. O refresh token fica salvo no armazenamento privado do app e é usado para renovar o acesso sem novo login.

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

| Mensagem | Causa provável |
|---|---|
| "Conta não possui Minecraft" | A conta não comprou o Java Edition (Game Pass precisa ter o perfil Java criado em minecraft.net) |
| Erro XSTS 2148916233 | Conta sem perfil Xbox — entre uma vez em xbox.com |
| Erro XSTS 2148916238 | Conta de menor sem família configurada |
