# Scripts das figuras e das checagens do artigo

Tudo aqui roda com Node, sem dependências. As simulações precisam do JDK 25 (nesta máquina,
`C:\Program Files\Java\jdk-25.0.2\bin\java.exe`; o `java` do PATH é o 21 e não serve) e o
`pdf.js` precisa do Chrome ou do Edge.

| Arquivo | O que faz |
|---|---|
| `figs.js` (com `svg.js` e `lib.js`) | Gera `Fig1` a `Fig4` em SVG e HTML a partir das rodadas |
| `pdf.js` | Imprime cada `Fig*.html` em PDF vetorial |
| `stats.js` | Calcula os números da Seção 3.2 e da Tabela 3 para uma rodada |
| `hash-run.js` | SHA-256 de cada saída de uma rodada (CSV descompactado); compara com um arquivo de hashes |
| `s17-full.sha256`, `s17-frozen-2016.sha256` | Hashes das 49 saídas das duas rodadas usadas no artigo |
| `synapse-2016-switch.patch` | Interruptor que religa o defeito das sinapses de 2016 |
| `check-tex.js` | Checagem estática do `.tex` (chaves, ambientes, rótulos, citações, tabelas) |

## 1. Rodadas

As duas pastas têm de se chamar `s17-full` e `s17-frozen`, dentro de uma mesma pasta de rodadas.

Motor atual, com o CLI do repositório:

```powershell
& "C:\Program Files\Java\jdk-25.0.2\bin\java.exe" -jar remoto-cli\target\remoto-cli-4.0.0-SNAPSHOT.jar run scenarios\remoto-17-default-soleus-contraction.json --threads 4 -o C:\rodadas\s17-full
```

Com o defeito de 2016: aplique o patch **numa cópia do repositório**, não na pasta de trabalho
(o site roda de `target/classes`), compile o CLI lá e rode com a propriedade ligada. Sem a
propriedade, o programa com o patch dá exatamente as saídas do original.

```powershell
git apply paper\scripts\synapse-2016-switch.patch
```

```powershell
.\mvnw.cmd -pl remoto-cli -am package -DskipTests
```

```powershell
& "C:\Program Files\Java\jdk-25.0.2\bin\java.exe" "-Dremoto.frozenStep=0.05" -jar remoto-cli\target\remoto-cli-4.0.0-SNAPSHOT.jar run scenarios\remoto-17-default-soleus-contraction.json --threads 4 -o C:\rodadas\s17-frozen
```

Confira que as rodadas são as do artigo (a resposta esperada é `identical: 49 outputs`):

```powershell
node paper\scripts\hash-run.js C:\rodadas\s17-full paper\scripts\s17-full.sha256
```

```powershell
node paper\scripts\hash-run.js C:\rodadas\s17-frozen paper\scripts\s17-frozen-2016.sha256
```

Os hashes valem para o motor `remoto-legacy-2b5-rng1-syn1` e foram gerados no Windows 11 com o
Java 25.0.2 da Oracle. Se o motor mudar de versão, gere-os de novo (`hash-run.js` sem o segundo
argumento) e atualize os números do artigo.

## 2. Figuras e números

```powershell
node paper\scripts\figs.js C:\rodadas C:\rodadas\figuras
```

```powershell
node paper\scripts\pdf.js C:\rodadas\figuras
```

Copie os `Fig*.pdf` e `Fig*.svg` para `paper\figures`. As figuras 1 e 2 são diagramas e não
dependem das rodadas; a 3 usa `s17-full`; a 4 usa as duas.

```powershell
node paper\scripts\stats.js C:\rodadas\s17-full
```

## 3. Checagem do manuscrito

```powershell
node paper\scripts\check-tex.js paper
```

Não substitui a compilação: só aponta chaves e ambientes desbalanceados, rótulos e citações sem
destino, caracteres especiais soltos e linhas de tabela com o número errado de colunas.
