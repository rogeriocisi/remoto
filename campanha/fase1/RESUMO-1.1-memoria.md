# Fase 1.1 — o teto de memória: medido, diagnosticado, com correção

Rodado em 28/11/2026. Classificação: **defeito confirmado, de alta gravidade.**

## O sintoma

`remoto-25-position-task.json`, na duração que o próprio cenário declara (30 s), `--threads 4`,
heap padrão desta máquina (5,33 GB, ergonômico, de 21,3 GB de RAM):

```
   70%
java.lang.OutOfMemoryError: Java heap space
```

Morreu aos **70%, depois de 13 minutos**. Não é limitação de hardware: é um cenário público que
acompanha o simulador e não termina pela linha de comando num computador de 21 GB.

## A medida

Repetido com `-Xmx10g`: **completa**, em ~28 min, e os números explicam tudo.

| medida | valor |
|---|---|
| pico de heap antes de uma coleta | **10,00 GB** (o teto dado) |
| maior heap **depois** de uma coleta | **9,81 GB** |
| último GC | 7452 MB → 7452 MB (**não liberou nada**) |
| *full GCs* | 0 |
| saída em disco | 775 MB, 180 séries |

Memória viva de 9,81 GB significa que quase nada é liberável: o motor **retém tudo até o fim**.
Um GC que devolve 7452 MB → 7452 MB é a confirmação direta.

## A causa

`LegacyOutputs.series`, linhas 107-123:

```java
private static void series(ResultSink sink, String name, String unit, List<?> store) {
    double[] t = new double[store.size()];
    double[] v = new double[store.size()];
    for (Object item : store) {
        if (item instanceof Signal s) { t[n] = s.getTime(); v[n] = s.getValue(); n++; }
    }
```

Cada amostra de cada série é um **objeto na heap** — `br.remoto.core.simulation.util.Signal`, com
um `String type`, um `double value` e um `double time` — acumulado numa `List`, e só convertido
para `double[]` no final.

Aritmética para uma série de 30 s a 0,05 ms, isto é 600.000 amostras:

| forma de guardar | por amostra | 600 mil amostras | × 180 séries |
|---|---|---|---|
| `Signal` em `List` | ~48 B (cabeçalho 16 B + referência + 2 doubles + slot da lista) | ~29 MB | **~5,2 GB** |
| dois `double[]` paralelos | 16 B | ~9,6 MB | ~1,7 GB |

Mais as cópias transitórias de `Arrays.copyOf` na conversão. Bate com os 9,81 GB observados.

O `String type` é o agravante silencioso: uma referência repetida 600.000 vezes por série para
guardar um rótulo que é o mesmo da série inteira.

## Medido depois da correção de nível 1 — e a previsão estava errada

Com `DoubleSeries` no lugar da lista de objetos, o mesmo cenário:

| | antes | depois |
|---|---|---|
| maior heap vivo depois de uma coleta | 9,81 GB | **8,08 GB** |
| coletas | 124 | 107 |

**Cerca de 18%, não os 3× previstos acima.** A aritmética da seção anterior supõe 600.000
amostras por série, mas o `Sample` **decima** antes de guardar (`decimationFrequency`), então as
séries nunca tiveram esse tamanho e os objetos `Signal` nunca foram o grosso da memória. A
correção é real e vale — menos alocação, menos coletas, resultado idêntico — mas **não resolve o
problema**: 8,08 GB continua muito acima do heap padrão de 5,33 GB, e os seis cenários de 30 s
continuam sem terminar sem `-Xmx`.

Onde a memória está de fato, ainda por confirmar com histograma de heap em vez de estimativa:
os trens de disparo são o candidato seguinte, e são guardados pior que as séries —
`somaSpikeTrain.add(new Double(t))` em `Motoneuron:238` e `Interneuron:81` usa o construtor
descontinuado, que aloca um objeto novo a cada disparo, e `terminalSpikeTrain` faz o mesmo em
`Motoneuron:246`, `NeuralTract:66` e `SensoryFiber:31`. São milhões de disparos numa simulação
de 30 s com alguns milhares de células.

**Próximo passo: medir com `jcmd GC.class_histogram`, não estimar de novo.**

## A correção, em três níveis

1. **Imediato, ~3× de memória, sem mudar resultado.** Acumular em `double[]` crescentes (ou
   `DoubleArrayList`) em vez de `List<Signal>`, e tirar o `String type` de dentro da amostra. Os
   números produzidos são os mesmos, bit a bit: só muda o recipiente.
2. **De raiz, memória constante.** Escoar cada série para o `ResultSink` ao longo da simulação,
   em blocos, em vez de acumular até o fim. Resolve para qualquer duração, e é o que permite o
   cenário de 30 s rodar em qualquer máquina.
3. **Enquanto 1 e 2 não existem.** Estimar a memória necessária a partir de duração, passo e
   número de séries, e **recusar a execução com mensagem clara** em vez de morrer aos 70% depois
   de 13 minutos. O `MemoryGuard` que faria isso existe, mas é *package-private* em
   `br.remoto.web.sim` e inalcançável pela CLI.

## Alcance

Seis cenários públicos têm 30 s simulados — `remoto-19`, `remoto-24`, `remoto-25`, `remoto-26`,
`remoto-27`, `remoto-28` — e outros sete ficam entre 12 e 20 s. Pelo servidor web o
`MemoryGuard` provavelmente interrompe antes do OOM, o que troca a morte por uma recusa; pela
CLI não há nada.

## Teste de regressão a acrescentar

Rodar `remoto-25-position-task.json` na duração declarada com `-Xmx2g` e exigir que **termine**.
Hoje falha com 5,33 GB; depois da correção de nível 2 deve passar com folga. É o teste que
transforma este defeito em algo que não volta.
