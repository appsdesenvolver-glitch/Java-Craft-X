# Auditoria dos executáveis Windows

Análise **estática** (o arquivo nunca foi executado), feita numa pasta isolada em 26/11/2026.

## minecraft-1-17-9230-35541.exe (recebido)

| Item | Resultado |
|---|---|
| Tamanho | 33.908.768 bytes |
| SHA-256 | `0fc1ded9a9459789b76104275682e603868181a0e1928ec0681810e7c8e2bf17` |
| SHA-1 | `5069c9d61d180af0ed8924cb951423fc4ca74511` |
| MD5 | `afc010d82c412d72c66f51768671a976` |
| Tipo | PE32, interface gráfica, assembly .NET (Mono/.Net), x86, 3 seções (.text, .rsrc, .reloc) |
| Data de compilação (cabeçalho PE) | 09/04/2025 19:44 UTC |
| Recursos de versão | FileDescription/ProductName **MinecraftInstaller**, OriginalFilename **MinecraftInstaller.exe**, versão **1.17.9230.35541** |
| Assinatura digital | Authenticode presente (10.272 bytes). Signatário: **Microsoft Corporation**, emitido por *Microsoft Code Signing PCA 2011* → *Microsoft Root Certificate Authority 2011*. A cadeia foi lida; a verificação criptográfica completa do Authenticode não foi executada (exige ferramenta Windows/osslsigncode, indisponível aqui) |
| Conteúdo | Interface WPF (XAML), textos de licença em RTF, referências a "Minecraft Launcher" e "GamingServices". **Nenhum ZIP, MSIX/APPX, JAR ou arquivo do jogo embutido** |
| URLs encontradas | Apenas Microsoft (aka.ms, go.microsoft.com, crl/pki da Microsoft) e esquemas XML. Nenhum servidor desconhecido |

**Conclusão:** é o instalador oficial da Microsoft para Windows. Ele não contém o Minecraft: serve para
instalar o *Minecraft Launcher* do Windows (e os Gaming Services da Microsoft), que por sua vez baixa o jogo
depois do login. Tudo nele é específico do Windows (.NET/WPF, Gaming Services) e não pode rodar no Android.

**O que foi aproveitado:** o *processo* equivalente — consultar metadados oficiais, baixar o jogo dos
servidores da Mojang após login Microsoft e conferir hashes — já está implementado no JAVA CRAFT X
(Version Hub + instalador do motor). Nenhum byte do EXE foi copiado para o app.

## MinecraftInstaller (1).exe e minecraft-1-17-9230-35541 (1).exe (recebidos de novo)

SHA-256 `0fc1ded9a9459789b76104275682e603868181a0e1928ec0681810e7c8e2bf17`: **byte a byte idênticos** ao
instalador oficial da Microsoft analisado acima. Mesma conclusão.

## TLauncher-Installer-1.9.8.2.exe (recebido)

| Item | Resultado |
|---|---|
| Tamanho | 70.489.032 bytes |
| SHA-256 | `fe4db6d1009ec399d9b1eb9270f1d080ab33428a7af2a23be957bcc56a95b28d` |
| Tipo | PE32 x86 (Windows), 5 seções; executável nativo (não .NET) |
| Assinatura digital | Authenticode presente. Signatário: **TLauncher Inc.**, Victoria, Mahé, **Seicheles** (C=SC), emitido por *Certum Code Signing 2021 CA* (Asseco Data Systems, Polônia). Verificação criptográfica completa não executada |
| Estrutura | Instalador montado com **Indigo Rose Setup Factory** (scripts Lua: `init.lua`, `lua5.3.dll`) que também carrega um pacote **WiX Burn**; ~68,8 MB de dados comprimidos anexados ao final (overlay), com `TL-INSTALLER.exe` e `setup.exe` |
| Conteúdo | Payload comprimido/ofuscado; não foi descompactado nem executado. Menções a componentes Java (JRE) no texto do instalador |
| URLs legíveis | Só de certificados (Sectigo, Certum, Microsoft PKI) e `indigorose.com`. Os servidores que o instalador contata depois não aparecem em texto claro |

**Conclusão:** o TLauncher **não é da Mojang nem da Microsoft**. É um launcher de terceiros para Windows, conhecido
por permitir jogar sem uma conta com licença. Ele não pode rodar no Android (executável x86 do Windows) e
**não foi integrado**: integrar seria usar uma fonte não oficial do jogo e contornar a licença, o que este projeto não
faz. Nenhum arquivo dele foi copiado para o app.

## Fonte gratuita da versão completa

Não existe fonte legítima que forneça gratuitamente o Minecraft: Java Edition completo sem licença.
As opções legítimas no JAVA CRAFT X são:

1. **Conta Microsoft que possui o Java Edition** → versão completa, sem limite de tempo.
2. **Conta Microsoft sem o jogo** → a **demonstração oficial**, com o limite definido pela Mojang.

Pesquisa feita em 26/11/2026 no site oficial (minecraft.net/free-trial e central de ajuda da Mojang): a demo do
Java Edition é liberada **depois do login** para contas Microsoft que não possuem o jogo, dura cerca de 100 minutos
(5 dias de jogo), é só para um jogador no modo Sobrevivência, sem comandos e sem servidores online (LAN permitida).
É exatamente o fluxo implementado no JAVA CRAFT X. Não foi encontrada nenhuma outra fonte oficial e gratuita do
Java Edition completo.
