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

## MinecraftInstaller.exe e TLauncher-Installer-1.9.8.2.exe (não recebidos)

Esses dois arquivos não chegaram nesta conversa, então não foram analisados. Observação: o TLauncher é um
launcher de terceiros, não da Mojang/Microsoft; ele não seria usado como fonte de arquivos do jogo.

## Fonte gratuita da versão completa

Não existe fonte legítima que forneça gratuitamente o Minecraft: Java Edition completo sem licença.
As opções legítimas no JAVA CRAFT X são:

1. **Conta Microsoft que possui o Java Edition** → versão completa, sem limite de tempo.
2. **Conta Microsoft sem o jogo** → a **demonstração oficial**, com o limite definido pela Mojang.
