# Matriz de compatibilidade

Valores **esperados** a partir do motor Amethyst/PojavLauncher — não são resultados de testes do JAVA CRAFT X
(ver [RELATORIO_TESTES.md](RELATORIO_TESTES.md)). Compatibilidade universal não é garantida.

## Requisitos

| Item | Mínimo | Recomendado |
|---|---|---|
| Android | 5.0 (API 21) | 10+ |
| Arquitetura | ARM64, ARM32, x86, x86_64 | ARM64 |
| RAM | 2 GB (versões antigas) | 4 GB+ para 1.18+ |
| Armazenamento | ~1 GB livre | 3 GB+ com mods |
| GPU | OpenGL ES 2.0 | OpenGL ES 3.2 ou Vulkan 1.1 |

## Versões do Minecraft × runtime

| Versões | Java | Origem do runtime |
|---|---|---|
| ≤ 1.16.5 | 8 | Embutido no APK |
| 1.17 – 1.20.4 | 17 | Baixado na 1ª execução |
| 1.20.5 – 1.21.x | 21 | Baixado na 1ª execução |
| Snapshots 26.x+ | 25 | Baixado na 1ª execução |

## Renderizadores

| Renderizador | Melhor para | Limitações |
|---|---|---|
| Krypton (NG-GL4ES) | GPUs antigas, até 1.21.4 | Sem shaders modernos |
| MobileGlues | 1.17+ em GLES 3.2 | Exige GPU com GLES 3.2 |
| Zink (Vulkan) | Adreno/Mali com Vulkan bom | Mais pesado; drivers variam |
| LTW | Experimental | — |

## Shaders

Shaders dependem de Iris/OptiFine **e** de um renderizador que exponha recursos de OpenGL desktop.
Em GPUs de entrada (ex.: PowerVR GE8320 do Moto E13) espere que a maioria **não funcione**.

## Moto E13 (2 GB RAM, Unisoc T606/T612, PowerVR/Mali de entrada)

Expectativa: 1.12.2–1.16.5 com Krypton, heap 700–900 MB, render distance 3–5. 1.20+ só com Sodium e
tende a ficar no limite da memória.
