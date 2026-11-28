# Inventário dos modelos biofísicos de `remoto-core`

Levantamento item por item de **todos** os modelos biofísicos em
`remoto-core/src/main/java`, feito por leitura direta dos arquivos em 2026-11-28.

**Escopo e método.** Percorri os 145 arquivos `.java` do módulo, pacote por pacote.
Nada aqui vem de memória ou de inferência: cada parâmetro, constante, equação e citação
foi lido no arquivo indicado, e o caminho (com a linha, quando ajuda a localizar) vem
junto. Não alterei nenhum arquivo de código, não rodei build nem simulação.

**Como o módulo está organizado.** Há duas famílias de código de modelo, e isso importa
para ler o inventário:

| Árvore | O que é | Linhas |
| --- | --- | --- |
| `br.remoto.core.model.*` | Os modelos reescritos: canais, membranas, sinapses, receptores, músculos, EMG, articulações, aleatoriedade. Java puro, sem dependência do resto. | ~7.300 |
| `br.remoto.core.simulation.*` | A camada de simulação: o laço, as fábricas, os *value objects* da configuração e **um segundo conjunto de classes de modelo** (`simulation.neuron`, `simulation.musculotendon`, `simulation.joint`, `simulation.proprioceptor`), que são o porte direto do ReMoto original e envolvem/duplicam parte dos modelos de `model.*`. | ~16.300 |

Um achado estrutural (ver **Achados**, A-01) é que os dois conjuntos coexistem: por
exemplo, há `model.muscle.HillMuscle` **e** `simulation.musculotendon.Hill`,
`model.receptor.MileusnicSpindle` **e** `simulation.proprioceptor.MuscleSpindle`.

**Convenção de unidades do módulo** (declarada em
`model/neuron/ContinuousMotoneuronMembrane.java:19-22` e repetida em
`model/neuron/Membrane.java`): potenciais medidos **a partir do repouso** em mV,
condutâncias em µS, capacitâncias em nF, correntes em nA, tempo em ms. O axônio de
Bostock é a exceção: trabalha em mV absolutos, pF e pA
(`model/neuron/AxonNode.java:68-70`).

**Legenda de status.**

- **Implementado** — o modelo calcula e o resultado chega a um observável.
- **Implementado, desligado por padrão** — o código existe e está correto, mas um
  parâmetro ou uma escolha de configuração o mantém inerte.
- **Parcial** — o modelo calcula parte do que a fonte descreve, ou um ramo nunca produz
  valor útil.
- **Inoperante** — o código roda mas o resultado é constante/zero por construção.

---

# 1. Canais iônicos — `br.remoto.core.model.channel`

Quatro classes. Família de **pulso**: em vez de integrar Hodgkin-Huxley a cada passo,
cada disparo abre um pulso de duração fixa e as comportas seguem soluções fechadas
dentro e fora dele.

## 1.1 `PulseChannel` — `model/channel/PulseChannel.java`

**O que modela.** Classe-base selada (`permits SodiumChannel, GatedChannel`) de uma
condutância iônica no modelo de pulsos.

**Fonte citada (literal, linhas 4-6):**

> "Condutância iônica no modelo de pulsos (Destexhe, 1997), como no ReMoto original: cada
> disparo do neurônio abre um pulso de duração fixa, e as comportas seguem soluções
> fechadas dentro e fora dele, em vez das equações de Hodgkin-Huxley integradas a cada
> passo."

**Equação.** `current(t, v) = value(t) · (e − v)` (linha 32), com `value(t) = gmax ·
Π(comportas)`. Note a convenção: a corrente é `g·(E − V)`, positiva para dentro.

**Parâmetros.**

| Parâmetro | Unidade (do comentário) | Linha |
| --- | --- | --- |
| `gmax` | µS — "Condutância máxima (µS)" | 14-15 |
| `e` | mV — "Potencial de reversão (mV)" | 16-17 |
| `pulseDuration` | ms | 18 |

**Estado.** `inPulse`, `t0` (instante da última troca de estado), `value`.
`start(t)` (linha 54) alterna o estado; o pulso **termina sozinho** quando
`t − t0 > pulseDuration` (linha 39).

**Variantes selecionáveis.** Nenhuma nesta classe; as variantes são as subclasses.

**Status.** Implementado.

**Como testar.** Pelo potencial de ação do soma de um motoneurônio (`vs`): a amplitude e
a largura do AP saem direto de `gmax`/`pulseDuration` do sódio e do potássio rápido.
Observável acessível: o sinal de `vs` em resposta a um disparo forçado. Comparar a
forma do AP (duração ~1 ms, amplitude até o teto de 120 mV) com Cisi e Kohn (2008),
figura do AP somático.

**Achado embutido:** linhas 47-49, `if (Double.isNaN(value)) value = lastValue;` — um
NaN na condutância é silenciosamente substituído pelo valor anterior (ver F-03).

## 1.2 `PulseGate` — `model/channel/PulseGate.java` (package-private)

**O que modela.** Uma comporta (m, h, n, q, p) no modelo de pulsos.

**Equações (linhas 29-38), as duas soluções fechadas:**

- fora do pulso (decaindo para 0): `x(t) = v₀ · e^(−β(t − t₀))`
- dentro do pulso (subindo para 1): `x(t) = 1 + (v₀ − 1) · e^(−α(t − t₀))`

onde `v₀` é o valor na última troca de estado e `t₀` o instante dela
(`changeState`, linhas 41-44).

**Parâmetros.** `alpha` (taxa de subida, 1/ms), `beta` (taxa de descida, 1/ms).

**Status.** Implementado.

**Comentário que admite divergência (linha 22):**

> "Volta ao repouso. Como no original, o último valor calculado não é zerado."

Ou seja, `reset()` zera `v0` e `t0` mas **não** `value` (ver F-01).

## 1.3 `SodiumChannel` — `model/channel/SodiumChannel.java`

**O que modela.** Sódio com ativação e inativação.

**Equação (linha 36):** `g = gmax · m³ · h`. No pulso, `m` sobe e `h` cai
(linhas 29-32); fora dele, o contrário (linhas 32-35).

**Parâmetros** (construtor, linhas 12-18, unidades do javadoc):
`gmax` (µS), `e` (mV), `pulseDuration` (ms), `alphaM`, `betaM`, `alphaH`, `betaH`.

**Status.** Implementado.

**Como testar.** Limiar e tempo de subida do AP. Com `gammaCa = 0` e uma corrente
injetada em rampa no soma, a reobase da curva f–I deve cair quando `gmax` do Na sobe.

## 1.4 `GatedChannel` — `model/channel/GatedChannel.java`

**O que modela.** Uma comporta elevada a um expoente. Javadoc (linhas 3-6):

> "Uma comporta elevada a um expoente, g = gmax·xᵏ, subindo durante o pulso e decaindo
> fora dele. Cobre o potássio rápido (n⁴), o potássio lento (q²) e o cálcio persistente
> (p)."

**Variantes selecionáveis (fábricas estáticas), com a potência de cada uma:**

| Fábrica | Equação | Papel | Linha |
| --- | --- | --- | --- |
| `fastPotassium` | `g = gmax·n⁴` | potássio retificador retardado | 19-22 |
| `slowPotassium` | `g = gmax·q²` | potássio lento da **AHP** | 24-27 |
| `calcium` | `g = gmax·p` | **cálcio persistente do dendrito (PIC)** | 29-32 |

Não há padrão: quem monta a membrana escolhe (ver `NeuronFactory`, §9.4).
O `switch` em `conductance` (linhas 38-42) usa expoentes constantes, com o comentário
"expoentes constantes, como no original, para a JIT especializar o `Math.pow`".

**Parâmetros.** `gmax` (µS), `e` (mV), `pulseDuration` (ms), `alpha`, `beta`,
`exponent` (4, 2 ou 1).

**Status.** Implementado.

**Como testar.**

- *Potássio lento (q²)*: a **AHP** depois de um disparo isolado — amplitude e duração.
  Comparar com Cisi e Kohn (2008): AHP de ~1-5 mV e dezenas de ms, mais longa nas
  unidades S que nas FF.
- *Potássio rápido (n⁴)*: a repolarização do AP e a taxa máxima de disparo.
- *Cálcio (p)*: ver §2.5 (PIC).

---

# 2. Membranas e neurônios — `br.remoto.core.model.neuron`

Nove classes. É a maior superfície de modelo do módulo, e a que mudou mais
recentemente (a família de cinética contínua).

## 2.1 `Membrane` (interface selada) — `model/neuron/Membrane.java`

**O que define.** O contrato mínimo de qualquer membrana, independente da formulação dos
canais. `permits SpinalMembrane, MotoneuronMembraneModel` (linha 19).

**Comentário de projeto (linhas 10-14), que explica o desenho:**

> "Deliberadamente fora daqui: os canais de pulso (`na`, `kf`, `ks`), que são da
> formulação de `SpinalMembrane` e não de toda membrana, e tudo que pressupõe dois
> compartimentos, que fica em `MotoneuronMembraneModel`. É essa separação que permite uma
> segunda família de membrana — com cinética contínua, por exemplo — sem herdar os campos
> da primeira."

**Grandezas e unidades declaradas.**

| Método | Unidade (javadoc) |
| --- | --- |
| `vs()` | mV, "medido a partir do repouso" |
| `capacitanceSoma()` | nF |
| `gLeakSoma()` | uS |
| `threshold()` | "mV acima do repouso" |
| `tSomaSpike()` | ms, "ou o 'nunca' do modelo" |
| `dendExcit/dendInhib/somaExcit/somaInhib` | `SynapseGroup` |

**Status.** Implementado (interface).

## 2.2 `MembraneSettings` (interface) — `model/neuron/MembraneSettings.java`

**O que define.** Os parâmetros de simulação que a membrana lê a cada passo.

| Método | Unidade/semântica (javadoc, linhas 10-23) |
| --- | --- |
| `step()` | "Passo de integração h (ms)" |
| `stepByTwo()` | "h/2 (ms)" |
| `stepBySix()` | "h/6 (ms)" |
| `gammaCa()` | **"Ganho γ da corrente persistente de cálcio do dendrito; 0 desliga a PIC"** |
| `motoneuronSomaRefractoryPeriod()` | "Período refratário do soma dos motoneurônios (ms)" |

`gammaCa` é o interruptor global da PIC nas duas formulações (ver §2.5 e §2.6).

## 2.3 `MotoneuronMembraneModel` (interface selada) — `model/neuron/MotoneuronMembraneModel.java`

**O que define.** A dinâmica de dois compartimentos, **sem** pressupor formulação:
`permits MotoneuronMembrane, ContinuousMotoneuronMembrane` (linha 19).

**Comentário (linhas 10-13):**

> "O contrato é de dinâmica, não de formulação: o dendrito e o soma avançam um passo, o
> soma informa se cruzou o limiar, e a PIC é consultada depois. Como nada aqui pressupõe
> canais de pulso, uma membrana de cinética contínua implementa esta interface sem herdar
> de `SpinalMembrane`; basta entrar na cláusula `permits`."

**Contrato de passo.** `stepDendrite(t)` → `stepSoma(t)` (retorna se cruzou o limiar)
→ `checkPersistentInwardCurrent(t)`.

**Grandezas próprias.** `vd()` (mV), `capacitanceDend()` (nF), `gLeakDend()` (uS),
`gCoupling()` ("Condutância de acoplamento entre soma e dendrito (uS)"),
`thresholdCa()` ("Limiar do soma que dispara a PIC (mV acima do repouso)"),
`tDendritePIC()` (ms), `calciumConductance()` (µS, "já com o ganho γ, para os sinais
gravados").

## 2.4 `SpinalMembrane` (classe-base selada) — `model/neuron/SpinalMembrane.java`

**O que modela.** A formulação de **canais de pulso** do ReMoto original.
`permits MotoneuronMembrane, InterneuronMembrane` (linha 20).

**Constantes do modelo (linhas 22-29), todas com unidade declarada:**

| Constante | Valor | Significado |
| --- | --- | --- |
| `E0` | 0.0 mV | "Potencial de repouso, referência das fugas" |
| `P0` | **−16.0 mV** | "Limite inferior do potencial" |
| `ESE` | **120.0 mV** | "Limite superior do potencial" |
| `NEVER` | −1 000 000 | "'Nenhum disparo ainda': o mesmo −T_MAX do original" |

`saturate(v)` (linhas 75-83) prende o potencial em `[P0, ESE]` — aplicado em **cada
estágio** do Runge-Kutta, não só no resultado.

**Canais.** `na`, `kf`, `ks` (`PulseChannel`, linhas 39-41). `fireSoma(t)`
(linhas 68-73) abre os três.

**Reset.** `reset()` põe `tSomaSpike = NEVER`, `vs = 0` e reseta os canais que existirem
(guardas `!= null`, linhas 56-64).

**Status.** Implementado.

## 2.5 `MotoneuronMembrane` — `model/neuron/MotoneuronMembrane.java` ⭐

**O que modela.** O **motoneurônio de dois compartimentos com canais de pulso de
Destexhe** — o modelo canônico do ReMoto.

**Fonte citada (literal, linhas 7-14):**

> "Motoneurônio de dois compartimentos (Cisi e Kohn, 2008): o soma, com os canais de pulso
> de sódio e potássio e as sinapses somáticas, e o dendrito, com a corrente persistente de
> cálcio (PIC) e as sinapses dendríticas, ligados por uma condutância de acoplamento.
>
> A cada passo o dendrito é integrado primeiro, com o potencial do soma do passo anterior,
> e depois o soma, com o dendrito já atualizado: dois Runge-Kutta de 4ª ordem em
> sequência, como no original."

**Equações.**

Dendrito (`dVdDt`, linhas 84-101):

```
C_d dVd/dt = γCa·I_Ca(t,Vd)              [só se gammaCa > 0]
           + I_dendExcit + I_dendInhib
           + g_c·(Vs − Vd)
           + g_leak,d·(E0 − Vd)
           + I_inj,d(t)
```

Soma (`dVsDt`, linhas 103-119):

```
C_s dVs/dt = I_Na + I_Kf + I_Ks
           + I_somaExcit + I_somaInhib
           + g_c·(Vd − Vs)
           + g_leak,s·(E0 − Vs)
           + I_inj,s(t)
```

Comentário de unidades na linha 99: "correntes em nA, capacitância em nF".

Integração: RK4 clássico com pesos `(k1 + 2k2 + 2k3 + k4)·h/6`
(linhas 46-52 e 60-68), com `saturate` em cada estágio.

**Detecção de disparo (linha 67):** `vs > threshold && t − tSomaSpike >
motoneuronSomaRefractoryPeriod()`.

**PIC — corrente persistente de entrada (linhas 70-82):**

```java
if (settings.gammaCa() > 0) {
    if (vs > thresholdCa && t - tDendritePIC > PIC_INTERVAL) {
        startPersistentInwardCurrent(t);
    }
}
```

- `PIC_INTERVAL = 0.05` ms (linha 19): "Intervalo mínimo entre dois inícios da PIC (ms)".
- Aqui a PIC é um **evento**: cruzar `thresholdCa` abre o canal de pulso de cálcio.
- O ganho entra na **corrente**, não na condutância (linha 89:
  `iIonic += ca.current(t, v) * settings.gammaCa()`), e o comentário de
  `calciumConductance()` (linhas 175-178) registra a escolha:

  > "O último valor do canal de pulso do cálcio (µS). Aqui o ganho γ entra na corrente,
  > não na condutância, então o valor é o do canal, como o original gravava."

**Parâmetros aceitos** (por *setters*, todos com unidade no javadoc da interface):
`vs`, `vd` (mV), `capacitanceSoma`, `capacitanceDend` (nF), `gLeakSoma`, `gLeakDend`,
`gCoupling` (µS), `threshold`, `thresholdCa` (mV acima do repouso), `na`, `kf`, `ks`,
`ca` (`PulseChannel`), os quatro `SynapseGroup`, e `setInjection(soma, dendrite)`
(`DoubleUnaryOperator`, nA).

**Variantes selecionáveis.** É uma das duas implementações de
`MotoneuronMembraneModel`; a outra é `ContinuousMotoneuronMembrane`. **Esta é o padrão**
(ver §9.4, `NeuronFactory`).

**Status.** Implementado. A PIC é **implementada mas desligada por padrão** quando
`gammaCa = 0` (ver §9.4 para o valor efetivo na configuração).

**Como testar.**

1. **f–I e reobase.** Injetar corrente em degraus crescentes no soma
   (`InjectedCurrent`) e medir a frequência de disparo em regime. Esperado: limiar
   (reobase) e inclinação primária da curva f–I. Comparar com Cisi e Kohn (2008),
   fig. de f–I: faixa de ~6-30 Hz para unidades S e inclinações de alguns Hz/nA, com
   as unidades FF recrutando mais tarde e disparando mais rápido.
2. **AHP.** Disparo único → medir amplitude e constante de tempo da pós-hiperpolarização
   (vem de `ks`, o q²).
3. **Acoplamento.** Com `gCoupling` variando, medir a atenuação de um degrau de corrente
   dendrítica vista no soma: razão `Δvs/Δvd` em regime deve ser
   `g_c/(g_c + g_leak,s)`.
4. **PIC/biestabilidade.** Com `gammaCa > 0`, uma rampa de corrente triangular deve
   produzir **histerese** no recrutamento (o motoneurônio desliga em corrente mais baixa
   do que ligou) e, se γ for alto, autossustentação. Comparar com os platôs de
   Hounsgaard/Heckman reproduzidos em Cisi e Kohn (2008).
5. **Período refratário.** A frequência instantânea máxima não pode passar de
   `1000/motoneuronSomaRefractoryPeriod` Hz.

## 2.6 `ContinuousMotoneuronMembrane` — `model/neuron/ContinuousMotoneuronMembrane.java` ⭐ (630 linhas)

**O que modela.** A **segunda família de membrana, de cinética contínua**: mesma
topologia de dois compartimentos, mas com comportas de Hodgkin-Huxley integradas a cada
passo em vez dos pulsos de Destexhe.

**Fonte citada e descrição (literal, linhas 7-32):**

> "Motoneurônio de dois compartimentos com cinética contínua: a mesma topologia de
> `MotoneuronMembrane` — soma e dendrito acoplados —, com as condutâncias iônicas
> descritas por comportas de Hodgkin-Huxley integradas a cada passo, em vez dos pulsos de
> duração fixa de Destexhe (1997).
>
> No soma são cinco comportas: sódio rápido (m³h), sódio persistente (p³), potássio rápido
> (n⁴) e potássio lento (r²). No dendrito, a comporta L do cálcio tipo L, cuja
> condutância é escalada pelo ganho de neuromodulação γ. A diferença que importa em
> relação ao modelo de pulso é que a corrente persistente passa a ser um estado da
> membrana, e não um evento disparado pelo limiar do soma: é daí que vêm os platôs de
> despolarização e a biestabilidade.
>
> **Unidades.** As mesmas do resto do ReMoto: potenciais medidos a partir do repouso (mV),
> condutâncias em µS, capacitâncias em nF e correntes em nA. É também a convenção em que o
> conjunto de parâmetros de origem está escrito, com E_Na = 120, E_K = −10 e a fuga
> revertendo no repouso.
>
> **Integração.** As comportas avançam por Euler exponencial, que é a solução exata de
> x' = (x∞ − x)/τ com v congelado no passo — o mesmo que o método `cnexp` das formulações
> de origem. Os potenciais avançam por Runge-Kutta de 4ª ordem, como no modelo de pulso,
> com as comportas congeladas dentro do passo. O dendrito é integrado primeiro, com o
> potencial do soma do passo anterior, e depois o soma, com o dendrito já atualizado.
>
> **Disparo.** Aqui o potencial de ação é produzido pela própria dinâmica, então
> `fireSoma(double)` apenas registra o instante, para quem cuida do axônio e das colisões,
> e `checkPersistentInwardCurrent(double)` não faz nada: a PIC é contínua."

Note que a citação é genérica ("as formulações de origem", "o conjunto de parâmetros de
origem"): **nenhum artigo é nomeado** para a cinética nem para os coeficientes
(ver F-10).

**Condutâncias (linhas 217-223, `dVsDt`):**

```
g_Na  = gNaBar · m³ · h
g_Nap = gNapBar · p³
g_Kf  = gKfBar · n⁴
g_Ks  = gKsBar · r²
I_iônica = (g_Na + g_Nap)·(E_Na − V) + (g_Kf + g_Ks)·(E_K − V)
```

Dendrito (linha 200): `I_iônica = gCaLBar · caL · γCa · (E_CaL − Vd)`.

**Taxas α e β — as dez funções, transcritas (linhas 242-280).** Todas usam
`u = v − vTraubSoma`:

| Comporta | α | β |
| --- | --- | --- |
| m (Na rápido, ativ.) | `0.64 · vtrap(15 − u, 4)` | `0.56 · vtrap(u − 40, 5)` |
| h (Na rápido, inativ.) | `0.928 · exp((17 − u)/18)` | `9.0 / (exp((40 − u)/5) + 1)` |
| p (Na persistente) | `0.64 · vtrap(5 − u, 4)` | `0.56 · vtrap(u − 30, 5)` |
| n (K rápido) | `0.08 · vtrap(15 − u, 7)` | `2.0 · exp((10 − u)/40)` |
| r (K lento) | `3.5 / (exp((55 − u)/4) + 1)` | **constante `rInact`** |
| L (Ca tipo L, dendrito) | — | — (usa `lInf` e `lTau` direto) |

`vtrap(x, y)` (linhas 282-288), "Evita o 0 no denominador das taxas, como na formulação
de origem":

```java
if (Math.abs(x / y) < 1e-6) return y * (1 - x / y / 2);
return x / (Math.exp(x / y) - 1);
```

`lInf(v)` (linhas 278-280): `1 / (exp(−((v − vTraubDend) − L_HALF)) + 1)`, com
`L_HALF = −30.0` ("Meia-ativação da comporta L, relativa a `vTraubDend` (mV)",
linhas 55-56). **Sem fator de inclinação** — ver F-04, um dos achados mais importantes
deste inventário.

**Integração das comportas (linhas 177-192).** Euler exponencial:

```java
advanceGateFromRates(gate, α, β):  sum = α+β;  if (sum <= 0) return gate;
                                   return advanceGate(gate, α/sum, 1/sum);
advanceGate(gate, inf, tau):       if (tau <= 0) return inf;
                                   return inf + (gate − inf)·exp(−step/tau);
```

**Reset (linhas 106-119).** Potenciais em 0 e **comportas no estado estacionário desse
potencial** — `m = α/(α+β)` em v = 0, etc., e `caL = lInf(0)`. Chamado no construtor
(linha 103), ao contrário de `MotoneuronMembrane` (ver F-11).

**Parâmetros aceitos, com valor-padrão no campo quando há.**

| Parâmetro | Unidade (javadoc) | Padrão no campo | Linha |
| --- | --- | --- | --- |
| `gNaBar` | µS, "Condutância máxima do sódio rápido" | 0 (sem *setter* → inerte) | 66, 523-530 |
| `gNapBar` | µS, sódio persistente | 0 | 67, 532-539 |
| `gKfBar` | µS, potássio rápido | 0 | 68, 541-548 |
| `gKsBar` | µS, potássio lento | 0 | 69, 550-557 |
| `gCaLBar` | µS, cálcio tipo L no dendrito | 0 | 87, 559-566 |
| `eNa` | mV acima do repouso | **120.0** | 70, 568-575 |
| `eK` | mV em relação ao repouso | **−10.0** | 71, 577-584 |
| `eCaL` | mV acima do repouso | **140.0** | 88, 586-593 |
| `vTraubSoma` | mV, "Deslocamento de potencial das taxas do soma" | **0.0** | 72, 595-602 |
| `vTraubDend` | mV, "Deslocamento de potencial da comporta L" | **35.0** | 89, 604-611 |
| `lTau` | ms, "Constante de tempo da comporta L" | **60.0** | 90, 613-620 |
| `rInact` | 1/ms, "Taxa de inativação do potássio lento" | **0.05** | 73, 622-629 |

Mais os herdados de `Membrane`/`MotoneuronMembraneModel`: capacitâncias, fugas,
`gCoupling`, `threshold`, `thresholdCa`, os quatro `SynapseGroup`, `setInjection`.

**Observáveis expostos para teste (linhas 303-363)** — e este é o ponto forte desta
classe para validação: `gateM()`, `gateH()`, `gateP()`, `gateN()`, `gateR()`, `gateL()`,
`gNa()`, `gNap()`, `gKf()`, `gKs()`, `gCaL()` ("já escalada por γ") e
`calciumConductance()`.

**Variantes selecionáveis.** É a alternativa a `MotoneuronMembrane`. **Não é o padrão.**

**Status.** Implementado. **Desligado por padrão** no sentido de que a formulação de
pulso é a escolhida por omissão, e de que `gCaLBar = 0` até alguém chamar o *setter*.

**Como testar.**

1. **Comparação lado a lado com o modelo de pulso.** O mesmo cenário f–I com as duas
   membranas: as curvas f–I devem bater na faixa de disparo tônico; a diferença esperada
   está no AP (aqui emerge da cinética, lá é um pulso de duração fixa) e na PIC.
2. **Forma do AP.** Aqui o AP tem subida/descida próprias: medir amplitude, largura a
   meia altura e taxa máxima de subida (dV/dt). Comparar com registros intracelulares de
   motoneurônio de gato/humano (AP de 50-80 mV sobre o repouso, 0,5-1,5 ms de largura).
   Nesta implementação o teto é `ESE = 120 mV`, que é também `eNa` — então o AP **não
   pode** passar de 120 mV, e a corrente de Na se anula exatamente no teto.
3. **AHP via r².** Com `rInact = 0.05`/ms, a constante de tempo de relaxação de `r`
   longe do limiar é ~`1/rInact = 20 ms`. O decaimento da AHP deve mostrar essa escala.
   Teste direto: um disparo isolado, ajustar uma exponencial no retorno de `vs` ao
   repouso e conferir τ ≈ 20 ms.
4. **Sódio persistente (`p³`).** Com `gNapBar > 0`, esperar redução da reobase e
   aparecimento de disparo subliminar/ruidoso. Observável: `gateP()` e `gNap()` em função
   de `vs` em regime; `p∞(v)` deve ser uma sigmoide com meia-ativação ~5 mV acima do
   repouso (de `P_ALPHA_V = 5`), bem abaixo da de `m` (15 mV) — é isso que faz dela
   "persistente".
5. **PIC contínua e platôs.** Com `gCaLBar > 0` e `gammaCa > 0`, uma rampa triangular de
   corrente injetada deve dar **histerese** e, com γ suficiente, um platô autossustentado
   depois de a corrente voltar a zero. Observável direto: `gateL()` e `gCaL()`.
   Como `lTau = 60 ms`, o platô liga/desliga nessa escala de tempo.
6. **Verificação do `vtrap`.** Teste unitário puro: `vtrap(x, y)` deve ser contínuo em
   `x → 0`, com limite `y`. Confere-se que `vtrap(1e-9, 4) ≈ 4` e que não há
   descontinuidade na vizinhança de `|x/y| = 1e-6`.
7. **Estado estacionário.** Depois de `reset()`, com nenhuma entrada, os potenciais devem
   ficar em 0 **se** as correntes iônicas em repouso se cancelarem. Como a fuga reverte
   em `E0 = 0` e as comportas estão no estacionário, qualquer corrente iônica residual em
   v = 0 desloca o repouso: medir `vs` após 1 s sem entrada é um teste de consistência
   dos `gBar` escolhidos (ver F-05).

## 2.7 `InterneuronMembrane` — `model/neuron/InterneuronMembrane.java`

**O que modela.** O **interneurônio de um compartimento** — e portanto **as células de
Renshaw e os interneurônios Ia, Ib e do grupo II**, todos a mesma classe.

**Javadoc (literal, linhas 5-9):**

> "Interneurônio de um compartimento (célula de Renshaw, interneurônios Ia, Ib e do grupo
> II): soma com os canais de pulso de sódio e potássio e as sinapses, que no original
> ficam nos grupos 'dendríticos' do neurônio, integrado por Runge-Kutta de 4ª ordem."

**Equação (`dVsDt`, linhas 36-52):**

```
C_s dVs/dt = (I_Na + I_Kf + I_Ks) + g_leak·(E0 − V) + (I_dendExcit + I_dendInhib) + I_inj
```

Com o comentário na linha 50: **"a ordem da soma é a do original, diferente da do
motoneurônio"** — isto é, uma divergência de ordem de operações preservada
deliberadamente para reprodutibilidade bit a bit.

**Parâmetros.** Os de `SpinalMembrane`, mais `somaRefractoryPeriod` ("Período refratário
do soma deste tipo de interneurônio (ms)", linhas 59-66) e `setInjection` (um só
compartimento).

**Detecção de disparo (linha 33), com o comentário que admite a assimetria
(linhas 31-32):**

> "a comparação é t > último + refratário, e não t − último > refratário como no
> motoneurônio: em ponto flutuante as duas não são equivalentes, e cada uma fica como era"

**Variantes selecionáveis.** Nenhuma: não há família contínua de interneurônio
(ver F-02).

**Status.** Implementado.

**Como testar.**

1. **Célula de Renshaw — inibição recorrente.** Estimular o nervo supramaximalmente e
   medir a depressão da resposta reflexa subsequente; ou injetar corrente num pool de
   motoneurônios e medir a redução de taxa atribuível às Renshaw (desligar as sinapses
   MN→RC e comparar). Comparar com a redução de ganho descrita em Cisi e Kohn (2008).
2. **Interneurônio Ia — inibição recíproca.** Estimular o nervo antagonista e medir a
   supressão do reflexo H do agonista no intervalo de ~2-4 ms de condicionamento.
3. **f–I do interneurônio.** Corrente injetada em degraus; as Renshaw devem atingir
   taxas bem mais altas que os motoneurônios (centenas de Hz em rajada), o que depende de
   `somaRefractoryPeriod` ser pequeno.
4. **Teste de regressão da assimetria.** Um teste que confirme que o interneurônio usa
   `t > tSpike + refract` (e não a forma do motoneurônio) protege a reprodutibilidade
   declarada.

## 2.8 `AxonNode` — `model/neuron/AxonNode.java` (380 linhas) ⭐

**O que modela.** O **axônio mielinizado humano no ponto do estímulo do nervo**: um nó
de Ranvier e o internó vizinho, acoplados pela via de Barrett-Barrett.

**Fonte citada (literal, linhas 6-17):**

> "O axônio mielinizado no ponto do estímulo do nervo: um nó de Ranvier e o internó
> vizinho, ligados pela via de Barrett-Barrett (sob e através da mielina), no modelo de
> axônio humano de Bostock, com os parâmetros de Howells, Trevillion, Bostock e Burke
> (2012, J Physiol 590:1625-1640), tabelas 1 e 2 e apêndice, para axônios motores e
> sensitivos.
>
> No nó: sódio transitório (`m³h`) e persistente (`mp³`, 1,07% da permeabilidade), pela
> equação de Goldman-Hodgkin-Katz com seletividade de 0,9 para o Na⁺; potássio rápido
> (`n⁴`) e lento (`s`); vazamento. No internó: potássio rápido e lento, a corrente h
> (HCN, `q`, seletividade de 0,097 para o Na⁺) e vazamento. As bombas de Na⁺/K⁺ são
> correntes constantes. As capacitâncias são a do nó, a da mielina e a do axolema
> internodal; as concentrações e as constantes de velocidade, as do artigo, a 36 °C."

**Duas divergências que o próprio comentário admite (literal, linhas 19-28)** — material
de primeira para a seção de achados:

> "Duas escolhas desta implementação, por o apêndice publicado no PubMed Central ter as
> equações cortadas à esquerda:
>
> - as equações das duas tensões são as do circuito da figura 1 (a mielina entre o espaço
>   periaxonal e o meio externo), sem desprezar nenhum termo; o artigo divide a do internó
>   só por `C_ax`, o que dá o mesmo resultado, porque a capacitância da mielina é 200
>   vezes menor;
> - as correntes das bombas são as que mantêm os potenciais de repouso da tabela 1
>   (−84,4 mV no nó e −84,6 mV no internó do motor). **Calculadas assim, elas dão −41 e
>   −9,2 pA no motor, contra −33 e −7,9 pA da tabela: algum detalhe do modelo original
>   difere desta reconstrução.**"

**Escala do estímulo (literal, linhas 30-34):**

> "O estímulo entra como corrente no nó. A escala é a do limiar da configuração (a
> corrente, em mA, de um pulso de 1 ms): a corrente injetada é `estímulo / limiar` vezes a
> corrente que faz o modelo disparar com um pulso de 1 ms. Assim, os limiares de cada
> axônio continuam valendo, e o modelo dá o resto: a curva intensidade-duração (constante
> de tempo de cerca de 0,4 ms no motor e 0,5 ms no sensitivo), a refratariedade, a
> supernormalidade e a acomodação."

**Integração (literal, linhas 36-39):**

> "A integração usa Euler exponencial nas comportas e Euler implícito linearizado nas
> tensões, com subpassos de até 0.005 ms enquanto o nó está ativo (estímulo,
> despolarização, disparo) e um passo por passo da simulação quando ele só se recupera.
> Parado no repouso, o modelo não calcula nada."

**Constantes físicas e de controle.**

| Constante | Valor | Linha |
| --- | --- | --- |
| `FINE_STEP` | 0.005 ms — "o limiar fica a menos de 1% do convergido" | 48-49 |
| `SPIKE` | 0 mV — "O nó dispara quando a tensão passa deste valor subindo" | 51-52 |
| `REARM` | −60 mV — rearma abaixo | 53 |
| `ACTIVE` | −75 mV — "Acima desta tensão, o nó é integrado com subpassos finos" | 55-56 |
| `FARADAY` | 96485.33 | 58 |
| `GAS` | 8.314462 | 59 |
| `TEMPERATURE` | 273.15 + 36 (**36 °C**) | 60 |
| `RT_F` | `GAS·T/FARADAY·1000` (mV) | 61 |
| `NA_I`, `NA_O` | 9, 144.2 mM | 63-64 |
| `K_I`, `K_O` | 155, 4.5 mM | 65-66 |
| `C_NODE` | 1.4 pF | 68 |
| `C_MYELIN` | 1.55 pF | 69 |
| `C_AXOLEMMA` | 327 pF | 70 |
| `SEL_NA` | 0.9 (seletividade Na⁺ do canal de sódio) | 72 |
| `SEL_H` | 0.097 (seletividade Na⁺ da corrente h) | 73 |
| `E_K` | `reversal(0)` | 74 |
| `E_H` | `reversal(SEL_H)` | 75 |

Taxas de Kf e Ks, "iguais no motor e no sensitivo (tabela 2)" (linhas 77-81), como
`{A, B, C}`:

| Taxa | A | B (mV) | C (mV) |
| --- | --- | --- | --- |
| `ALPHA_N` | 0.0221 | −90.8 | 7.7 |
| `BETA_N` | 0.0393 | −73.6 | 7.35 |
| `ALPHA_S` | 0.00563 | −23.5 | 12.7 |
| `BETA_S` | 0.00341 | −91.1 | 11.7 |

**Variantes selecionáveis: `Kind.MOTOR` e `Kind.SENSORY`** (enum, linhas 43-46), com
dois `Params` completos (record, linhas 85-104). Transcrição dos dois conjuntos:

| Campo | MOTOR | SENSORY |
| --- | --- | --- |
| `pNa` | 4.35e-9 | 4.35e-9 |
| `napFraction` | 0.0107 | 0.0107 |
| `gKsNode` (nS) | 56.7 | 29.1 |
| `gKsInternode` | 0.57 | 1.74 |
| `gKfNode` | 18.2 | 19.4 |
| `gKfInternode` | 207 | 205 |
| `gH` | 2.95 | 4.1 |
| `gLeakNode` | 1.97 | 1.69 |
| `gLeakInternode` | 4 | 3.65 |
| `gBB` (Barrett-Barrett) | 35.9 | 40.3 |
| `restNode` (mV) | **−84.4** | **−80.3** |
| `restInternode` (mV) | **−84.6** | **−81.3** |
| `alphaM` {A,B,C} | 6.54, −18.5, 10.3 | 6.25, −18.3, 10.3 |
| `betaM` | 0.302, −22.8, 9.16 | 0.289, −22.6, 9.16 |
| `alphaMp` | 3.27, −36.5, 10.3 | 3.13, −36.3, 10.3 |
| `betaMp` | 0.151, −40.8, 9.16 | 0.145, −40.6, 9.16 |
| `alphaH` | 0.126, −115.1, 15.6 | 0.153, −113.8, 11.9 |
| `betaH` | 8.60, −32.9, 19.0 | 10.5, −31.6, 14.5 |
| `q` (HCN) | 0.00522, −107.3, −12.2 | 0.00522, −94.2, −12.2 |

**Equações.**

GHK para o sódio (`ghk`, linhas 323-333), com o comentário de unidades
("P (cm³/s) × F u × concentração (mM = 10⁻⁶ mol/cm³) dá A; × 10¹² dá pA"):

```
u = v / RT_F
I = pNa · F · u · [0.9·(Na_o − Na_i·e^u) + 0.1·(K_o − K_i·e^u)] / (1 − e^u) · 1e6
```

Correntes do nó (`nodeChannels`, linhas 297-309):

```
I_nó = (m³h + 0.0107·mp³)·GHK(v) + gKfNode·n⁴·(v − E_K) + gKsNode·s·(v − E_K)
       + gLeakNode·(v − restNode)
```

Correntes do internó (`internodeChannels`, linhas 311-321):

```
I_internó = gKfInternode·n*⁴·(v − E_K) + gKsInternode·s*·(v − E_K)
            + gH·q·(v − E_H) + gLeakInternode·(v − restInternode)
```

Circuito das duas tensões (comentário nas linhas 271-273 e código 274-288), resolvido
por Euler implícito linearizado com condutâncias numéricas (derivada por diferença
finita de 1e-3 mV):

```
(C_n + C_my) dE/dt  − C_my dE*/dt = I_inj − I_nó − I_BB
(C_ax + C_my) dE*/dt − C_my dE/dt = I_BB − I_internó
```

Formas funcionais das taxas (linhas 364-374):

- `f1(c,v) = A(E − B)/(1 − e^((B−E)/C))`, com guarda `|x| < 1e-7 → A·C`
- `f2(c,v) = A(B − E)/(1 − e^((E−B)/C))`, mesma guarda
- `betaH(v) = A/(1 + e^((B − v)/C))` (sigmoide, linhas 343-346)
- comporta `q`: `α = q0·x`, `β = q0/x` com `x = e^((E* − q1)/q2)` (linhas 268-269)

Potencial de reversão (`reversal`, linhas 376-379):
`RT_F · ln((sel·Na_o + (1−sel)·K_o) / (sel·Na_i + (1−sel)·K_i))`.

**Calibração do ganho (linhas 203-243).** `thresholdCurrent(kind, step)` faz uma
**busca binária** (50 iterações, `lo = 0`, `hi = 100 000` pA, tolerância relativa 1e-9)
pela corrente de um pulso de 1 ms que dispara o nó em até 5 ms, e **memoiza** o
resultado num `ConcurrentHashMap` por `kind + "@" + step` (linha 83, 208).
`gain = thresholdCurrent / threshold1ms` (linha 242); `threshold1ms ≤ 0` lança
`IllegalArgumentException`.

**Otimização de repouso (linhas 156-182, 245-255).** Se `stimulus == 0 && atRest`,
retorna sem calcular. Ao fim do passo, se o estado está a menos de 1e-4 mV e 1e-6 das
comportas do repouso (`nearRest`), **reseta ao repouso exato** e marca `atRest`.

**Status.** Implementado. É uma variante do mecanismo de disparo do axônio: escolhida
quando `useAxonNode()` é chamado (ver §2.9) ou pelo equivalente nos motoneurônios.
**Não é o padrão** — o padrão é o limiar de corrente (ou de carga, §4.2).

**Como testar.**

1. **Curva intensidade-duração (I-D).** Variar `pulseWidth` da estimulação e medir a
   amplitude mínima que faz o nó disparar. O próprio comentário dá o alvo: constante de
   tempo de **~0,4 ms no motor** e **~0,5 ms no sensitivo**, e a cronaxia deve ser
   `τ·ln2`. Comparar com os valores de excitabilidade de nervo periférico humano
   (Bostock/Burke): cronaxia motora ~0,3 ms.
2. **Potenciais de repouso.** `potential()` e `internodePotential()` logo depois da
   construção devem ser exatamente −84,4/−84,6 mV (motor) e −80,3/−81,3 mV (sensitivo).
   Teste trivial e de alto valor: fixa a tabela 1.
3. **Correntes das bombas.** `pumps()` expõe os dois valores; o comentário diz que dão
   **−41 e −9,2 pA** no motor contra **−33 e −7,9 pA** da tabela. Um teste que registre
   esses números documenta a divergência e detecta quando ela mudar (F-06).
4. **Refratariedade.** Dois pulsos com intervalo crescente: o limiar do segundo deve cair
   de infinito (período refratário absoluto, ~1 ms) a abaixo do normal
   (**supernormalidade**, ~5-10 ms), e depois voltar. É o "ciclo de recuperação" clássico
   das técnicas de *threshold tracking*.
5. **Acomodação.** Um pré-pulso despolarizante longo deve **aumentar** o limiar
   (acomodação por Ks e inativação de Na); um hiperpolarizante deve diminuí-lo e depois,
   pela corrente h, reverter. Comparar com as curvas de electrotonus limiar de Howells
   et al. (2012).
6. **Convergência numérica.** Reduzir `step` e conferir que o limiar calibrado converge:
   o comentário afirma menos de 1% de erro com `FINE_STEP = 0.005` ms.
7. **Consistência da memoização.** `thresholdCurrent` em paralelo, com duas
   `step` diferentes, deve dar dois valores distintos e estáveis.

## 2.9 `AfferentFiber` — `model/neuron/AfferentFiber.java` (297 linhas) ⭐

**O que modela.** A **fibra aferente** (Ia e II do fuso, Ib do órgão tendinoso): o
axônio dispara por estímulo elétrico **ou** pela taxa do receptor integrada num processo
gama.

**Fonte citada (literal, linhas 8-12):**

> "Fibra aferente (Ia e II do fuso, Ib do órgão tendinoso de Golgi): o axônio dispara
> quando o estímulo do nervo passa do limiar ou quando a taxa do receptor, acima do limiar
> de recrutamento, integrada num processo gama de ordem inteira, atinge o limiar sorteado
> (algoritmo de Gabbiani e Cox, 2010). Cada disparo chega à medula depois da latência de
> condução."

**Quatro fidelidades ao original, transcritas (linhas 14-23)** — todas são achados:

> "Fiel ao ReMoto original:
>
> - cada reset sorteia os limiares dos três tipos (ordens 25, 30 e 400), embora a fibra
>   use só o do seu; a integral da taxa não volta a zero no reset;
> - a taxa recebe a cada passo um ruído gaussiano de média 5 Hz e desvio 0,5 Hz;
> - o disparo pelo receptor é anotado no axônio com a latência até a placa motora somada
>   ao instante atual;
> - **depois do primeiro disparo, a fibra II sorteia o limiar com a ordem da Ia (25), não
>   com a sua (400).**"

**Ordem do processo gama (literal, linhas 28-31):**

> "Com uma ordem escolhida (como no ReMoto em Fortran, por tipo e músculo), a fibra usa
> essa ordem no seu limiar, no reset e depois de cada disparo; os outros dois sorteios do
> reset continuam os do original. **Ordem maior deixa o disparo mais regular: o
> coeficiente de variação dos intervalos, com a taxa constante, é `1/sqrt(ordem)`.**"

**Constantes.**

| Constante | Valor | Linha |
| --- | --- | --- |
| `T_MAX` | 1 000 000 ms ("O T_MAX do original") | 35-36 |
| `GAMMA_ORDER_IA` | **25** | 38 |
| `GAMMA_ORDER_IB` | **30** | 39 |
| `GAMMA_ORDER_II` | **400** | 40 |
| `MINIMAL_FIRING` | **5 Hz** — "Taxa mínima somada, com ruído, à taxa do receptor" | 42-43 |

**Variantes selecionáveis.**

1. **Tipo de fibra** — `Kind.IA("Ia")`, `Kind.IB("Ib")`, `Kind.II("II")`,
   `Kind.OTHER(null)` ("Tipo sem receptor: só o nervo a faz disparar", linhas 46-72).
   `Kind.of(code)` devolve `OTHER` para código desconhecido.
2. **Ordem gama** — `gammaOrder` no construtor; **padrão 0 = "as do original"**
   (construtor de 3 argumentos, linha 106, delega com 0). `gammaOrder < 0` lança
   `IllegalArgumentException`.
3. **Mecanismo de disparo pelo estímulo** — limiar de corrente (padrão) ou
   `AxonNode` sensitivo, via `useAxonNode()` (linhas 250-257): "Passa a usar um nó de
   Ranvier (de axônio sensitivo) no ponto do estímulo: é ele, e não o limiar de corrente,
   que decide quando o estímulo faz o axônio disparar."

**Equações / algoritmo (`generate`, linhas 177-211).**

```
estimulado = axonNode != null ? axonNode.step(stimulus)
                              : stimulus > axonThreshold && t − tAxonSpike > refract
se estimulado:   tAxonSpike = t;  anota disparo em t

se kind != OTHER e t − tAxonSpike > refract:
    rate = firingRate()
    se rate > recruitmentThreshold:
        λ = (rate − recruitmentThreshold) + gaussiana(5, 0.5)
        yInt += λ · step/1000
        se yInt >= thres:
            tAxonSpike = t + latencyStimulusEndPlate     // ← no futuro
            yInt = 0
            thres = gamma(1/ordem, ordem)                 // ordem conforme a variante
            anota disparo em tAxonSpike
```

Latências (`setLatency`, linhas 230-248):

```
latencyStimulusSpinal   = (d_spinal / v) · 1000      [ms]
latencyStimulusEndPlate = (d_endPlate / v) · 1000
// ambas arredondadas para múltiplo do passo: round(L/step)·step
// v < 0.01 m/s é elevado a 0.01
```

Entrega à medula (`deliver`, linhas 159-169): `nextSpike = axonSpikes[i] +
latencyStimulusSpinal`; entrega quando `t > nextSpike`, **um disparo por chamada**.

**Parâmetros aceitos.** `axonThreshold` (intensidade do estímulo),
`recruitmentThreshold` (Hz), `firingRate` (`DoubleSupplier`, Hz — "null = sem receptor
ligado"), e as três distâncias/velocidade de `setLatency` (m, m/s).

**Status.** Implementado. A variante `AxonNode` é implementada mas não é o padrão.

**Como testar.**

1. **Latência do reflexo H.** O observável central: estimular o nervo e medir o intervalo
   entre o estímulo e a resposta do músculo. A latência aferente é
   `latencyStimulusSpinal` (ida), somada ao atraso sináptico e à latência eferente. Para
   o sóleo humano, o H aparece em **~28-35 ms**. Teste direto: conferir que
   `latencyStimulusSpinal()` corresponde a `distância/velocidade` arredondado ao passo.
2. **Regularidade do disparo (ordem gama).** Com `firingRate` constante, medir o
   **coeficiente de variação** dos intervalos. O javadoc dá o alvo analítico:
   **CV = 1/√ordem**. Para Ia (25) → CV ≈ 0,20; para Ib (30) → 0,183; para II (400) →
   0,05. É um teste quantitativo forte e autossuficiente. **Mas** a fidelidade documentada
   (II redesenha com ordem 25 depois do primeiro disparo) significa que, **sem**
   `gammaOrder` explícito, a fibra II medirá CV ≈ 0,20 e não 0,05 (ver F-07).
3. **Taxa mínima.** Com `firingRate = 0` e `recruitmentThreshold = 0`, a fibra deve
   disparar a ~5 Hz (o ruído de média 5 Hz). Observável: taxa média de disparo do aferente
   em repouso. Confere a fisiologia de descarga tônica de aferentes Ia.
4. **Limiar de recrutamento.** Varrer `firingRate` e achar o ponto onde a fibra começa a
   disparar: deve ser exatamente `recruitmentThreshold` (com o piso de 5 Hz de ruído
   deslocando um pouco).
5. **Arredondamento da latência.** Teste: com `step = 0.05` ms, toda latência deve ser
   múltiplo de 0,05.

---

# 3. Sinapses — `br.remoto.core.model.synapse`

## 3.1 `SynapseGroup` — `model/synapse/SynapseGroup.java` (261 linhas) ⭐

**O que modela.** Todas as sinapses de um tipo (excitatórias ou inibitórias) num
compartimento, somadas com agregação eficiente.

**Fontes citadas (literal, linhas 7-17):**

> "As sinapses de um tipo (excitatórias ou inibitórias) num compartimento de um neurônio,
> somadas pela **agregação de Lytton (1996)** sobre a **cinética de Destexhe et al.
> (1994)**.
>
> Cada disparo pré-sináptico chega depois do atraso da sinapse e libera transmissor por um
> pulso de duração fixa. Durante o pulso, a fração ligada sobe para r∞ com constante τon;
> depois cai com τoff. Em vez de integrar uma equação por sinapse, o grupo guarda duas
> somas, a das sinapses em pulso (Ron) e a das demais (Roff), cada uma com solução fechada
> desde o último evento.
>
> A aritmética é a do `MultSynapticConductance` do ReMoto original, operação por operação,
> para que o resultado seja o mesmo bit a bit."

**Equações.** Derivadas dos parâmetros no construtor (linhas 61-70):

```
r∞   = (α·Tmax) / (α·Tmax + β)
τon  = 1 / (α·Tmax + β)
τoff = 1 / β
expFinish = e^(−pulseDuration/τon)
```

Solução fechada desde o último evento (`value`, linhas 118-120):

```
Ron(t)  = n_on·r∞ + (ron − n_on·r∞)·e^(−(t−t₀)/τon)
Roff(t) = roff·e^(−(t−t₀)/τoff)
g(t)    = gMaxTot · (Ron + Roff)
I(t,v)  = g(t)·(e − v)
```

Começo de pulso (`start`, linhas 189-236): a fração da sinapse migra de Roff para Ron,
com `ri = ri_anterior·e^(−(t − t_g − pulseDuration)/τoff)`, e
`n_on += gmax_efetivo/gMaxTot`. Um disparo durante o pulso **só prolonga o pulso**
(comentário na linha 219).
Fim de pulso (`stop`, linhas 239-260): `ri = r∞ + (ri − r∞)·expFinish`, e a fração volta
de Ron para Roff.

**Parâmetros do construtor, com unidade do javadoc (linhas 53-61):**

| Parâmetro | Unidade |
| --- | --- |
| `e` | mV, "potencial de reversão" |
| `pulseDuration` | ms, "duração do pulso de transmissor" |
| `tmax` | mM, "concentração de transmissor no pulso" |
| `alpha` | 1/(mM·ms), "taxa de ligação" |
| `beta` | 1/ms, "taxa de desligamento" |
| `active` | "se o grupo já começa ativo; senão, ativa no primeiro disparo recebido" |

Constante: `T_TOLERANCE = 0.001` ms ("Folga para comparar instantes de eventos com o
tempo da simulação").

**Cache de RK (linhas 45-48, 112-114, 147-152).** Comentário:

> "valor no último instante pedido, guardado só quando aquela chamada não começou nem
> terminou nenhum pulso: os estágios k2 e k3 do Runge-Kutta pedem o mesmo instante duas
> vezes seguidas"

**Comentário que admite comportamento estranho (linhas 72-76):**

> "Uma sinapse do neurônio pré-sináptico identificado por `key`. **Se a chave já existe, a
> sinapse nova a substitui, mas a condutância das duas continua somada no total**, como no
> original."

**Status.** Implementado.

**Como testar.**

1. **Forma do PSP unitário.** Um único disparo pré-sináptico num motoneurônio em repouso:
   medir amplitude, tempo ao pico e constante de decaimento do EPSP/IPSP. Alvos:
   tempo ao pico de **~1-2 ms** para Ia monossináptica, decaimento com τoff = 1/β.
2. **Verificação analítica de r∞ e τ.** Teste unitário puro: com α, β, Tmax dados,
   `value(t)` durante um pulso longo deve tender a `gMaxTot·r∞`, e o decaimento depois do
   pulso deve ajustar uma exponencial de constante `1/β`.
3. **Somação e saturação.** Dois disparos muito próximos devem somar sublinearmente
   (a fração ligada satura em r∞). Comparar com Lytton (1996).
4. **Reversão.** A corrente deve inverter de sinal quando `v` cruza `e`: teste direto de
   `current(t, v)`.
5. **Reprodutibilidade bit a bit.** O javadoc afirma igualdade bit a bit com o original:
   um teste de regressão com valores gravados (*golden values*) é o que sustenta essa
   afirmação.

## 3.2 `Synapse` — `model/synapse/Synapse.java`

**O que modela.** Um contato pré-sináptico dentro de um grupo. Javadoc (linhas 3-6):

> "Um contato pré-sináptico dentro de um `SynapseGroup`: a condutância máxima dele, o
> atraso e, para a agregação de Lytton, a fração ligada no fim do último pulso."

**Parâmetros.** `gmax` (µS, "Condutância máxima"), `delay` (ms, "Atraso sináptico"),
`dynamics` (`SynapticDynamics`).

**Estado.** `ri` (fração ligada), `t`, `endOfPulse`, `on`, `lastSpike`, `lastGmax`.
`reset()` põe `lastSpike = Double.NEGATIVE_INFINITY` com o comentário "só importa que
seja negativo: 'nenhum disparo ainda'" (linhas 30-31).

**Status.** Implementado.

## 3.3 `SynapticDynamics` — `model/synapse/SynapticDynamics.java` ⭐

**O que modela.** **Depressão e facilitação sinápticas.**

**Fonte citada (literal, linhas 3-7):**

> "Depressão ou facilitação de uma sinapse (**Capek e Esplin, 1977; Kohn et al., 1995**):
> a cada disparo a condutância máxima cai (ou sobe) `variation` por cento e volta ao valor
> de repouso com constante de tempo `tau` (ms)."

**Variantes selecionáveis (enum `Kind`, linha 10):** `NONE`, `DEPRESSING`,
`FACILITATING`. **Padrão: `NONE`** — a constante `SynapticDynamics.NONE`
(linhas 12-13), "Sem dinâmica: todo disparo tem a mesma condutância máxima".

**Equações** (implementadas em `SynapseGroup.start`, linhas 189-217):

```
se lastSpike > 0:
  DEPRESSING:   gLast ← gLast·(1 − variation/100)
  FACILITATING: gLast ← gLast·(1 + variation/100)
  gMax = gLast + (gmax − gLast)·(1 − e^(−(t − lastSpike)/tau))
```

Com o comentário na linha 205: **"variation/100 = 1 − p em Capek e Esplin (1977) e Kohn
et al. (1995)"** — isto é, `variation` é o complemento da probabilidade de liberação.

**Parâmetros.** `tau` (ms), `variation` (por cento).

**Status.** Implementado, **desligado por padrão** (`Kind.NONE`).

**Como testar.**

1. **Trem de pulsos em frequência fixa.** Estimular a mesma sinapse a 10, 20, 50 Hz e
   medir a sequência de amplitudes de PSP. Com `DEPRESSING`, deve decair para um platô;
   a razão `PSP_n/PSP_1` em regime é o alvo quantitativo. Comparar com Kohn et al.
   (1995) e com a depressão do EPSP Ia homônimo medida em humanos.
2. **Recuperação.** Dois trens separados por um intervalo crescente: a recuperação da
   amplitude deve ser exponencial com constante `tau`. Teste quantitativo direto.
3. **Facilitação.** Com `FACILITATING`, a sequência deve crescer e saturar.
4. **Teste do valor de `variation`.** `variation = 0` deve dar comportamento idêntico a
   `NONE` (bom teste de consistência).
5. **Atenção ao teste 1:** a depressão é **por sinapse**, não por grupo, e só vale se
   `lastSpike > 0` — ou seja, um disparo em `t = 0` exato não ativa a dinâmica
   (ver F-08).

---

# 4. Estímulos — `br.remoto.core.model.stimulus`

## 4.1 `NerveStimulation` — `model/stimulus/NerveStimulation.java`

**O que modela.** A **estimulação elétrica de um nervo**: um trem de pulsos e,
opcionalmente, um segundo trem pareado.

**Javadoc (literal, linhas 5-15):**

> "Estimulação elétrica de um nervo: pulsos de `pulseWidth` ms (`PULSE_WIDTH` no original,
> que não deixava mudar) entre `tini` e `tfin`, com o período modulado por um sinal, e
> opcionalmente um segundo trem de pulsos (pareado) que começa `delayPaired` ms depois. A
> intensidade de cada passo vai para os axônios dos motoneurônios e das fibras aferentes do
> nervo.
>
> Fiel ao ReMoto original: o sinal soma à frequência (o período é `1000/(freq + sinal)`); o
> trem principal avança um período sempre que o tempo se afasta do início do último pulso
> por um período ou mais, e o pulso dura enquanto o tempo está a até `pulseWidth` desse
> início, inclusive; o trem pareado é calculado pelos ciclos desde o seu próprio início."

**Constantes.** `PULSE_WIDTH = 1.0` ms ("o pulseWidth do original"),
`T_MAX = 1 000 000` ms ("abaixo de 1000/T_MAX, a frequência é zero").

**Equações (`step`, linhas 88-109):**

```
se t < tini ou t > tfin:  intensity = 0
período = 1000 / (freq + sinal(t))
se |t − start| >= período:  start += período
se |t − start| <= pulseWidth:  intensity = amp
se paired: intensity += stepPaired(t)
```

Trem pareado (`stepPaired`, linhas 111-126), por aritmética de ciclos:

```
tIniPaired = tini + delayPaired
cycle = (int)((t − tIniPaired)/periodPaired)
startPaired = tIniPaired + cycle·periodPaired
se t > startPaired e t < startPaired + pulseWidth + T_TOLERANCE: += ampPaired
```

**Parâmetros, com unidade do javadoc (linhas 41-59):**

| Parâmetro | Unidade / limite |
| --- | --- |
| `tini`, `tfin` | ms |
| `amp` | intensidade do estímulo (a escala de `axonThreshold`) |
| `freq` | Hz; "abaixo de 1000/T_MAX vira 0 e acima de 1000/step, um pulso por passo" |
| `signal` | `Modulation`, somado à **frequência** (obrigatório, não-nulo) |
| `paired` | se há segundo trem |
| `delayPaired` | ms |
| `ampPaired` | intensidade do segundo trem |
| `freqPaired` | Hz; "abaixo de 1000/T_MAX, a do trem principal" |
| `step` | ms |
| `pulseWidth` | ms, ">0"; **padrão `PULSE_WIDTH = 1.0`** (construtor de 10 args) |

**Variantes selecionáveis.** Trem simples (padrão) ou pareado; `pulseWidth` fixo em 1 ms
(construtor curto, o padrão) ou livre (construtor longo).

**Status.** Implementado.

**Como testar.**

1. **Razão H/M.** O observável mais informativo deste modelo. Varrer `amp` de zero ao
   supramáximo e medir, no EMG, a amplitude da **onda M** (direta, latência ~5-8 ms no
   sóleo) e do **reflexo H** (latência ~30 ms). A curva de recrutamento deve mostrar:
   H aparecendo antes do M (as Ia têm limiar mais baixo), H crescendo até um máximo,
   depois **decrescendo** por colisão antidrômica enquanto o M cresce até o platô.
   `H_max/M_max` para o sóleo humano é **~0,5-0,7**.
2. **Pulsos pareados / curva de recuperação do H.** Com `paired = true` e `delayPaired`
   variando de 1 a 500 ms, medir H2/H1. Esperado: depressão profunda abaixo de ~50 ms
   (refratariedade + inibição recorrente), recuperação lenta.
3. **Contagem de pulsos.** Teste puro: com `freq = 10` Hz de `tini` a `tini + 1000`,
   contar quantos passos têm `intensity > 0`; deve ser 10 pulsos × (1 ms/step) passos.
4. **Limites de frequência.** `freq = 0` e `freq > 1000/step` são casos de borda que
   merecem teste explícito (ver F-09).

## 4.2 `LeakyCharge` — `model/stimulus/LeakyCharge.java` ⭐

**O que modela.** O **limiar por carga com vazamento** — a alternativa leve ao `AxonNode`
para dar ao axônio uma curva intensidade-duração.

**Javadoc completo (literal, linhas 3-14):**

> "Limiar por carga com vazamento, para o estímulo do nervo: o axônio integra a
> intensidade com uma constante de tempo `tau` (a membrana perto do eletrodo, como um
> circuito RC) e dispara quando a integral passa do limiar. **Disso sai a curva
> intensidade-duração de Lapicque:** um pulso de duração `d` precisa de
> `I_reobase / (1 − exp(−d / tau))`, e **a cronaxia é `tau ln 2`**. Pulsos curtos precisam
> de mais corrente; os longos, de pouco mais que a reobase.
>
> O limiar da configuração (o do original, em corrente) continua valendo para um pulso de
> `NerveStimulation.PULSE_WIDTH` ms: a integral é dividida por
> `1 − exp(−PULSE_WIDTH / tau)`, e é esse valor que o axônio compara com o seu limiar. A
> integração é exata para a intensidade constante em cada passo; **com o passo de 0,05 ms,
> o pulso de 1 ms do original dura 21 passos, e o limiar efetivo fica cerca de 1% abaixo
> do nominal.**"

**Equações.**

```
decay         = e^(−step/tau)
normalization = 1 − e^(−PULSE_WIDTH/tau)
step(I):  charge ← I + (charge − I)·decay;  return charge / normalization
threshold(I_1ms, d, tau) = I_1ms · (1 − e^(−PULSE_WIDTH/tau)) / (1 − e^(−d/tau))
```

**Parâmetros.** `tau` (ms, "constante de tempo intensidade-duração do axônio", **>0, ou
`IllegalArgumentException`**), `step` (ms).

**Status.** Implementado. É uma variante do mecanismo de limiar; o padrão nominal é a
comparação direta de intensidade (ver §9 para qual a configuração escolhe).

**Como testar.**

1. **Curva I-D de Lapicque, analítica.** O método estático `threshold(I_1ms, d, tau)` dá
   o alvo teórico fechado. Teste: varrer a duração do pulso em `NerveStimulation`, achar
   numericamente o limiar e comparar com `LeakyCharge.threshold`. Erro esperado ~1%
   (o próprio javadoc declara o viés do passo).
2. **Cronaxia.** Medir a duração em que o limiar é o dobro da reobase; deve dar
   `tau·ln2`. Para nervo periférico humano, cronaxia ~0,3 ms → `tau ≈ 0,43` ms.
3. **Reobase.** Pulso muito longo (`d ≫ tau`): o limiar deve tender a
   `I_1ms·(1 − e^(−1/tau))`.
4. **Viés do passo.** Teste de regressão: com `step = 0.05` e `tau` dado, o limiar
   efetivo deve ficar ~1% abaixo do nominal, como o comentário afirma.

## 4.3 `InjectedCurrent` — `model/stimulus/InjectedCurrent.java`

**O que modela.** Corrente injetada num compartimento: `record(imax, signal)`.

**Equação (linhas 13-22):** `I(t) = imax + signal.value(t)` (ou só `imax` se
`signal == null`).

**Parâmetros.** `imax` (nA, "amplitude fixa"), `signal` (`Modulation`, "o sinal somado à
amplitude; null = nenhum").

**Status.** Implementado.

**Como testar.** É o instrumento de teste dos motoneurônios: degraus para f–I, rampas
para reobase e histerese de PIC, triângulos para biestabilidade.

---

# 5. Sinal modulante — `br.remoto.core.model.signal`

## 5.1 `Modulation` — `model/signal/Modulation.java` (262 linhas)

**O que modela.** O sinal que modula comandos descendentes, correntes injetadas,
estimulação de nervo e entradas biomecânicas. É um `record`.

**Javadoc (literal, linhas 6-21):**

> "Sinal que modula um comando descendente, uma corrente injetada, a estimulação de um
> nervo ou uma entrada biomecânica. Vale 0 fora da janela `[tini, tfin + T_TOLERANCE]`
> (ms).
>
> As expressões são as do ReMoto original, com as particularidades de algumas formas:
>
> - **a senoide perde 7 depois de 120 s;**
> - **a forma "custom" é a quadrada com amplitude fixa, 12,35 nos primeiros 10 s depois de
>   `tini` e 13,75 depois disso, qualquer que seja `amp`.** O original gravava essa
>   amplitude de volta no sinal; aqui o sinal não muda, e o valor calculado é o mesmo;
> - as formas aleatória e gama sorteiam a cada avaliação, em `Rng`.
>
> A forma "series", que o original não tinha (o ReMoto em Fortran aceita uma série de taxas
> qualquer), é uma série de pontos (tempo em ms, valor) com interpolação linear; dois
> pontos no mesmo tempo fazem um degrau. A janela vai do primeiro ao último ponto."

**Parâmetros (javadoc do record, linhas 23-30):**

| Parâmetro | Unidade / semântica |
| --- | --- |
| `shape` | `Shape` (não-nulo; use `UNDEFINED`) |
| `tini`, `tfin` | ms, a janela |
| `amp` | amplitude |
| `freq` | Hz, "da senoide, da AM e da quadrada" |
| `width` | ms, "largura do pulso da quadrada e das rampas; **na gama, a ordem**" |
| `delay` | ms, "atraso da senoide e da AM" |
| `modFreq` | Hz, "frequência da modulante da AM" |
| `modFactor` | "profundidade da modulação da AM" |
| `series` | pontos `t0, v0, t1, v1...`; "null nas outras formas" |

**Variantes selecionáveis (enum `Shape`, linhas 38-56) — 13 formas, com a equação de
cada uma (linhas 172-261):**

| Código | Forma | Equação |
| --- | --- | --- |
| `"none"` | `NONE` | 0 |
| `"constant"` | `CONSTANT` | `amp` |
| `"sine"` | `SINE` | `amp·sin(2πf t/1000 − 2πf·delay/1000)`, **menos 7 se `t > 120000`** |
| `"square"` | `SQUARE` | `amp` nos primeiros `width` ms de cada período `1000/freq` |
| `"ramp"` | `RAMP` | `amp·(t−tini)/width`, saturando em `amp` |
| `"custom"` | `CUSTOM` | quadrada com **amplitude fixa 12,35 / 13,75** (ignora `amp`) |
| `"random"` | `RANDOM` | `amp · Rng.random()` |
| `"triangle"` | `TRIANGLE` | sobe até `width`, depois `−amp·(t−tini)/width + 2amp` |
| `"trapezoid"` | `TRAPEZOID` | sobe até `width`, platô até `2·width`, depois desce |
| `"stochastic"` | `STOCHASTIC_GAMMA` | `Draws.gamma(amp, width)` — **sorteio a cada avaliação** |
| `"AM"` | `AM` | `amp·sin(2πf...)·(1 + modFactor·sin(2π·modFreq·t/1000))` |
| `"series"` | `SERIES` | interpolação linear dos pontos (busca binária, linhas 194-215) |
| `null` | `UNDEFINED` | **lança `IllegalStateException`** dentro da janela (linha 190) |

`Shape.of(code)`: `null → UNDEFINED`; código desconhecido → `NONE` (linhas 69-80).
Constante: `T_TOLERANCE = 0.001` ms ("a mesma do original").

**Validação da série (`checkSeries`, linhas 105-123):** pelo menos dois pontos
(`length >= 4`, par), todos finitos, tempos não decrescentes.
`parseSeries` (linhas 125-154) lê `"0 20; 1000 40"`; `formatSeries` escreve de volta.

**Status.** Implementado. `SERIES` é extensão própria (não existia no original).

**Como testar.**

1. **Formas analíticas.** Teste unitário por forma: amostrar `value(t)` e comparar com a
   expressão fechada. Alta cobertura por baixo custo.
2. **Janela.** `value(tini − ε) == 0` e `value(tfin + 2·T_TOLERANCE) == 0` para toda forma.
3. **Série.** Interpolação entre pontos, degrau quando dois pontos compartilham o tempo,
   extrapolação constante depois do último ponto (linhas 207-209).
4. **Ida e volta.** `parseSeries(formatSeries(p))` deve reproduzir `p`.
5. **Formas aleatórias.** `RANDOM` e `STOCHASTIC_GAMMA` **não são determinísticas no
   mesmo instante** — ver F-13; qualquer teste tem de fixar a semente e a ordem.

---

# 6. Tratos descendentes — `br.remoto.core.model.tract`

## 6.1 `TractSpikeGenerator` — `model/tract/TractSpikeGenerator.java` (274 linhas) ⭐

**O que modela.** Os **tratos descendentes como processos pontuais** (e os ruídos):
Poisson, gama de ordem inteira ou intervalos gaussianos, com a média modulada por um
sinal.

**Javadoc (literal, linhas 7-20):**

> "Disparos de um trato descendente ou de um ruído: um processo de Poisson, um processo
> gama de ordem inteira (integra a taxa até um limiar sorteado) ou intervalos gaussianos,
> com a média modulada por um sinal.
>
> Fiel ao ReMoto original, inclusive onde ele surpreende:
>
> - **o limiar do processo gama é sorteado a cada `reset()`, qualquer que seja a
>   distribuição, e a integral da taxa não volta a zero no reset;**
> - **o reset anota no trem do terminal um disparo inicial (em 0 ou no início do sinal)
>   também nos processos de Poisson e gama, embora esse disparo não chegue às sinapses;**
> - nos intervalos gaussianos, o trem recebe o próximo disparo já sorteado, e o reset
>   sorteia o primeiro intervalo duas vezes quando ele passa do início do sinal."

**Constantes.**

| Constante | Valor | Significado (javadoc) |
| --- | --- | --- |
| `T_MAX` | 1 000 000 ms | "Valor inicial do próximo disparo, o T_MAX do original" |
| `MAX_MEAN` | **2000 ms** | "Limite superior da média do intervalo quando o sinal modula a taxa" |
| `MIN_MEAN` | **0.5 ms** | "Limite inferior da média do intervalo: no máximo 2 kHz" |

**Variantes selecionáveis (enum `Distribution`, linhas 33-64):**

| Código | Distribuição | Algoritmo (linhas) |
| --- | --- | --- |
| `"P"` | `POISSON` | `p = λ·step/1000`; dispara se `Rng.random() <= p` (154-169) |
| `"Gm"` | `GAMMA` | `yInt += λ·step/1000`; dispara quando `yInt >= thres`, e re-sorteia `thres = Draws.gamma(1/std, std)` (171-187) |
| `"G"` | `GAUSSIAN` | dispara quando `t > tTerminalSpike`; próximo intervalo `Draws.gaussian(média, std)` (189-199) |
| `null`/desconhecido | `OTHER` | "o trato não dispara, mas o reset anota o disparo inicial" |

`Distribution.of(null)` **lança `NullPointerException`** (linhas 52-56) — diferente de
`Modulation.Shape.of`, que aceita null.

**Parâmetros do construtor, com unidade do javadoc (linhas 96-103):**

| Parâmetro | Unidade / semântica |
| --- | --- |
| `mean` | ms, "intervalo médio entre disparos; até `T_TOLERANCE`, o trato só dispara dentro da janela do sinal" |
| `std` | "desvio-padrão do intervalo gaussiano (ms); **no processo gama, a ordem**" |
| `signal` | `Modulation` que modula a média (obrigatório) |
| `modulatesRate` | "se o sinal soma à taxa (Hz) em vez de ao intervalo (ms)" |
| `step` | ms |
| `output` | `Output` (obrigatório) |

**Modulação da média (`meanValue`, linhas 224-252)** — a equação completa:

```
ret = mean;  mod = signal.value(t)
se modulatesRate:
    se mean > step:  mod += 1000/mean          // [Hz]
    se mod > step ou mod < −step:              // ← compara Hz com ms (F-12)
        ret = 1000/mod;  se ret > MAX_MEAN: ret = MAX_MEAN
    senão: ret = MAX_MEAN
senão: ret += mod
se ret < MIN_MEAN: ret = MIN_MEAN
```

**Interface `Output` (linhas 66-81):** `fire(t)` ("o disparo segue para as sinapses"),
`record(t)` ("Anota `t` no trem do terminal, que vai para as saídas"),
`unrecordFirst()` ("Desfaz a primeira anotação do trem"), `mean(t, mean)` (default
vazio).

**Status.** Implementado.

**Como testar.**

1. **Taxa média.** Com `mean` fixo e sinal nulo, contar disparos num intervalo longo: a
   taxa deve ser `1000/mean` Hz. Teste quantitativo direto para as três distribuições.
2. **Regularidade.** CV dos intervalos: **Poisson → CV ≈ 1**; **gama de ordem k →
   CV ≈ 1/√k**; **gaussiano → CV = std/mean**. É o teste que separa as três variantes e
   valida `Draws.gamma`.
3. **Modulação da taxa.** Com `modulatesRate = true` e um sinal em rampa, a taxa
   instantânea deve seguir `freq_base + sinal`. Observável: histograma de taxa
   instantânea versus tempo.
4. **Saturações.** `MIN_MEAN = 0.5` ms (2 kHz) e `MAX_MEAN = 2000` ms (0,5 Hz) são
   limites testáveis diretamente por `meanValue`.
5. **O disparo inicial espúrio.** A primeira fidelidade documentada diz que o reset anota
   um disparo em 0 ou em `tini` **que não chega às sinapses**. Um teste que compare o trem
   gravado com os disparos efetivamente entregues documenta essa diferença (F-14).

---

# 7. Aleatoriedade e gradientes — `br.remoto.core.model.random`

## 7.1 `Rng` — `model/random/Rng.java` ⭐

**O que modela.** A fonte de aleatoriedade, **um gerador por thread**, e o mecanismo que
garante reprodutibilidade.

**Javadoc (literal, linhas 5-12):**

> "A fonte de aleatoriedade das simulações: um gerador por thread.
>
> Quem roda uma simulação fixa a semente com `seed(long)` e obtém resultados
> reprodutíveis; sem semente, o comportamento é o dos antigos `Math.random()` e
> `new Random()`. **Os sorteios têm de acontecer sempre na mesma ordem: a atualização
> paralela dos neurônios marca as threads de trabalho com `forbid()`.**"

**Mecanismo.** `ThreadLocal<Random>` (linha 15). `FORBIDDEN` é um `Random` anônimo cujo
`next(int)` lança `ForbiddenDraw` (linhas 17-25). O comentário da exceção (linhas 27-30)
é notável:

> "Uma thread marcada com `forbid()` tentou sortear. **É um Error para que os
> `catch (Exception)` do código original não o engulam.**"

**API.** `seed(long)`, `reset()`, `forbid()`, `random()` ("no lugar de `Math.random()`"),
`newRandom()` ("Um gerador derivado da sequência atual, no lugar de `new Random()`").

**Status.** Implementado.

**Como testar.** Dois runs com a mesma semente devem dar saídas **idênticas bit a bit**;
dois com sementes diferentes, saídas diferentes. E um teste que rode a atualização
paralela e confirme que nenhuma `ForbiddenDraw` é lançada — ou, melhor, um teste negativo
que force um sorteio numa thread de trabalho e exija a `ForbiddenDraw`.
**Observação de teste crítica:** `newRandom()` consome um `nextLong()` da sequência, e
várias classes o chamam só para descartar (ver F-15) — qualquer mudança nelas quebra a
reprodutibilidade bit a bit.

## 7.2 `Draws` — `model/random/Draws.java`

**O que modela.** Os sorteios das distribuições usadas pelos modelos.

| Método | Equação | Fonte citada |
| --- | --- | --- |
| `gaussian(m, dp)` | `m + dp·(Σ₁¹² U − 6)` | "Aproximação de uma gaussiana pela soma de 12 uniformes (**Irwin-Hall**), como no ReMoto original" (linhas 9-12) |
| `gamma(a, k)` | `−a·ln(Π₁ᵏ U)` | "Gama de ordem inteira `k` e escala `a`, pelo produto de `k` uniformes" (linhas 21-25) |
| `uniform(v1, v2)` | `min + (max−min)·U`, "negativo vira 0" | — (linhas 34-51) |

**Comentário que admite comportamento estranho (linhas 23-25):**

> "Com `k` = 0 o produto fica 1 e o resultado é `−a·log(1)`; com `a` infinito, isso dá
> NaN, como no original."

**Status.** Implementado.

**Como testar.**

1. **`gaussian`.** Momentos amostrais: média, desvio, assimetria ≈ 0. **Mas** a soma de
   12 uniformes tem suporte **limitado a ±6 desvios** e curtose ligeiramente abaixo de 3:
   um teste de normalidade estrito (Kolmogorov-Smirnov) **falha** nas caudas. É um limite
   do método, não um bug — documentar o alvo correto (F-16).
2. **`gamma`.** Média amostral de `gamma(1/k, k)` deve ser 1; variância, `1/k`;
   CV, `1/√k`. Casa exatamente com o alvo de CV das fibras aferentes (§2.9) e dos tratos
   (§6.1).
3. **`gamma(a, 0)`.** Deve devolver 0 (e NaN com `a` infinito) — teste de borda explícito.

## 7.3 `Gradient` — `model/random/Gradient.java`

**O que modela.** Um parâmetro que **varia ao longo de um pool** de neurônios ou unidades
motoras. Javadoc (linhas 3-6): "`bias` vai de 0 (o primeiro do pool, o menor) a 1 (o
último)."

| Método | Equação | Observação |
| --- | --- | --- |
| `linear(v1, v2, bias)` | `v1 + (v2−v1)·bias` (ou, se `v2 ≤ v1`, `max − (max−min)·bias`) | "negativo vira 0"; "Quando `v2` não é maior que `v1`, a conta parte do maior valor, como no original" (linhas 12-15) |
| `linearWithNoise(v1, v2, bias, cv)` | `Draws.gaussian(linear(...), linear(...)·cv)` | "mais ruído gaussiano com coeficiente de variação `cv`" (linha 35) |

**Status.** Implementado.

**Como testar.** `linear(a, b, 0) == a`, `linear(a, b, 1) == b` para qualquer ordem de
`a` e `b`. E o observável de pool: a distribuição de reobase e de ganho f–I ao longo dos
índices do núcleo deve ser monótona — é isso que produz o **princípio do tamanho** no
recrutamento. Teste: recrutar o pool com uma rampa de comando descendente e verificar que
a ordem de recrutamento segue o índice.

---

# 8. Músculo, unidade motora e EMG

## 8.1 `MotorUnit` — `model/muscle/MotorUnit.java` (396 linhas) ⭐

**O que modela.** A **unidade motora**: os disparos na placa motora, os parâmetros do
abalo de cada modelo de músculo, e o **MUAP no EMG de superfície** com a posição da
unidade na secção do músculo.

**Fontes citadas (literal, linhas 5-12):**

> "Unidade motora: os disparos do motoneurônio na placa motora, os parâmetros do abalo de
> cada modelo de músculo, o estado que esses modelos avançam a cada passo e o potencial de
> ação da unidade (MUAP) no EMG de superfície, com a posição da unidade na secção do
> músculo.
>
> O MUAP é uma função de **Hermite-Rodriguez (Lo Conte et al.)**: de 1ª ordem nas unidades
> de índice par, de 2ª nas de índice ímpar, alargado e atenuado com a distância até a pele
> (**Fuglevand et al., 1992; Hermens et al., 1992**)."

**MUAP de Hermite-Rodriguez (`emg`, linhas 93-153).** A equação, com
`tA = t − t_disparo − 3·λ_eff`:

```
λ_eff = lambdaEMG · (emgAttenuation ? timeWidening : 1)
term  = (tA/λ_eff)²

1ª ordem (hrType == 1, índice par):
    tp  = λ_eff/√2
    aux = tp·e^(−(tp/λ_eff)²)              [= tp·e^(−0.5)]
    MUAP = (1/aux)·tA·e^(−term)

2ª ordem (hrType == 2, índice ímpar):
    MUAP = (1 − 2·term)·e^(−term)

contribuição = ampEMG · MUAP,  somada em todos os disparos com tA <= 6·λ_eff
e, se emgAttenuation, tudo multiplicado por attenuationToSkin
```

`hrType = index % 2 == 0 ? 1 : 2` (linha 77). O corte do laço (linhas 130-133): "o MUAP
já voltou ao repouso" quando `tA > 6·timeCte`.

**Atenuação e alargamento com a profundidade (`place`, linhas 203-209):**

```
attenuationToSkin = e^(−distância / tauAttenuation)
timeWidening      = 1 + cteWidening · distância
```

**Variantes selecionáveis: três distribuições espaciais.**

| Método | Algoritmo | Linhas |
| --- | --- | --- |
| `randomSpatialDistribution` | rejeição num círculo unitário (`y² + z² < 1`), **"desiste depois de 11 tentativas e fica no diâmetro horizontal"**; depois `y = y·(esp/2) + esp/2 + pele`, `z = z·esp/2` | 155-183 |
| `linearSpatialDistribution` | `y = (muNumber/(muTotal−1))·(espessura − pele) + pele`, `z = 0` | 185-194 |
| `presetSpatialDistribution` | `y`, `z` da configuração | 196-201 |

**Parâmetros do abalo aceitos** (campos com *setters*, linhas 36-46): `gpeak`, `gmax`,
`tpeak` (SOCDS/Hill), `gpeakRaikova`, `tpeakRaikova`, `tHalfRaikova`, `b`, `twTet`,
`bRaikova`, `twTetRaikova` (Raikova).

**Parâmetros do EMG:** `posY`, `posZ`, `attenuationToSkin`, `timeWidening`, `ampEMG`,
`lambdaEMG`, `emgAttenuation`.

**Identidade:** `index` ("índice do motoneurônio no núcleo, **de 1 em diante**"),
`type` ("tipo do motoneurônio (S, FR, FF)"), `cd`.

**Status.** Implementado.

**Como testar.**

1. **Forma do MUAP.** Um único disparo de uma única unidade: o sinal de EMG deve ser a
   função de Hermite-Rodriguez. Alvos quantitativos: a de **1ª ordem** é bifásica com
   pico normalizado em 1 (pela constante `aux`); a de **2ª ordem** é trifásica, vale
   `+1` em `tA = 0` e cruza zero em `tA = ±λ/√2`. Duração total ~`9·λ`. Comparar com
   MUAPs de superfície reais (duração 5-20 ms, amplitude 50-500 µV).
2. **Atenuação com a profundidade.** Colocar a mesma unidade em profundidades crescentes
   (`presetSpatialDistribution`) e medir a amplitude pico-a-pico: deve cair como
   `e^(−d/tauAttenuation)`, e a duração crescer como `1 + cteWidening·d`. É o teste do
   modelo de volume condutor de Fuglevand et al. (1992).
3. **Distribuição aleatória.** Rodar `randomSpatialDistribution` muitas vezes e conferir
   que as posições preenchem o círculo uniformemente — e que o caso de desistência (11
   tentativas) é raro (ver F-17).
4. **Alternância par/ímpar.** Verificar que unidades vizinhas no índice alternam entre
   MUAP de 1ª e 2ª ordem: é uma escolha do modelo que afeta o espectro do EMG somado.
5. **Espectro do EMG.** Com um pool inteiro ativo, a densidade espectral do EMG de
   superfície deve ter pico em **~50-100 Hz** e energia até ~400 Hz. Comparar com
   Hermens et al. (1992).

## 8.2 `AlphaTwitch` — `model/muscle/AlphaTwitch.java`

**O que modela.** O **abalo de uma unidade motora** como função alfa, implementado como
filtro IIR.

**Javadoc (literal, linhas 3-9):**

> "O abalo de uma unidade motora: a resposta a cada disparo é uma função alfa,
> `(t/T)·e^(1 − t/T)`, com pico em T. Calculada como um filtro IIR de segunda ordem,
> invariante ao impulso, cuja entrada é um impulso de área 1 por disparo.
>
> Usado pelo músculo SOCDS (o abalo) e pelo Hill (a ativação). As contas são as do
> `AlphaFunction` do ReMoto original, operação por operação."

**Coeficientes (`reset`, linhas 30-41):**

```
a1 = 2·e^(−step/tpeak)
a2 = −e^(−2·step/tpeak)
b1 = step · (1 · step/tpeak) · e^(1 − step/tpeak)
y[n] = a1·y[n−1] + a2·y[n−2] + b1·x[n−1]
```

(o `1 *` na linha 35 é um fator neutro herdado do original — ver F-18).

**Parâmetros.** `step` (ms, "passo de integração"), `tpeak` (ms, "instante do pico depois
do disparo").

**Disparo (`start`, linhas 48-56):** `x[N] = 1.0/step` — impulso de área 1 — e "o impulso
entra na saída a partir do passo seguinte" (porque `y[N]` usa `x[N−1]`).

**Status.** Implementado.

**Como testar.**

1. **Pico em `tpeak`.** Um disparo isolado: o máximo da saída deve ocorrer exatamente em
   `tpeak` ms depois, e o valor de pico deve ser 1 (função alfa normalizada). Teste
   analítico direto e de alto valor.
2. **Área.** A integral do abalo deve ser `tpeak·e` (área da função alfa com pico 1),
   independentemente do passo.
3. **Convergência.** Reduzir `step` e conferir que o pico converge para `tpeak` — a
   invariância ao impulso tem erro de ordem `step/tpeak`.
4. **Somação / razão tétano-abalo.** Um trem de disparos em frequência crescente: a
   força somada normalizada pelo abalo único dá a razão tétano/abalo. Para unidades
   humanas, `twTet` é tipicamente **2-6**.

## 8.3 `CriticallyDampedMuscle` (SOCDS) — `model/muscle/CriticallyDampedMuscle.java`

**O que modela.** O **músculo SOCDS** (*second-order critically damped system*): força =
soma dos abalos saturados das unidades.

**Javadoc (literal, linhas 5-12):**

> "Músculo de unidades motoras com abalo de 2ª ordem criticamente amortecido (SOCDS): a
> força de cada unidade é o abalo (`AlphaTwitch`) somado a cada disparo na placa motora,
> saturado por `2/(1 + e^(−b·f)) − 1` e escalado pela razão tétano/abalo e pelo pico do
> abalo; a força do músculo é a soma das unidades.
>
> **O comprimento normalizado fica em 1: o original somava ao do sóleo um triângulo de
> amplitude zero.** Velocidade e aceleração vêm da derivação numérica desse comprimento."

**Equações.**

```
f_unidade   = AlphaTwitch.value(iteração)
saturação   = 2/(1 + e^(−b·f)) − 1
F_unidade   = saturação · twTet · gpeak          [só se tipo ∈ {S, FR, FF}]
F_músculo   = Σ F_unidade
maximumMuscleForce = Σ (gpeak · twTet)           [calculada no construtor]

lengthNorm = 1 (sempre)
stretchVelocity     = (lengthNorm − lastNormLength)/step
stretchAcceleration = (stretchVelocity − lastStretchVelocity)/step
```

**Parâmetros.** `units`, `twitchStep` (ms, "passo com que os abalos são amostrados, o dos
motoneurônios"), `step` (ms, "para as derivadas do comprimento"). Por unidade:
`b`, `twTet`, `gpeak`, `tpeak`.

**Interface `UnitForceListener`** (linhas 16-19): recebe a força de cada unidade marcada
com `storedSignals()`.

**Status.** Implementado. O **comprimento é inoperante** (fixo em 1) — por construção,
documentado.

**Como testar.**

1. **Abalo único.** Uma unidade, um disparo: a força deve ser o abalo escalado. Pico em
   `tpeak`, amplitude `saturação(1)·twTet·gpeak`.
2. **Curva força-frequência.** Uma unidade, trens de 5 a 100 Hz: a força média
   normalizada pelo abalo deve subir sigmoidalmente até saturar em `twTet`. Comparar com
   as curvas de Bigland-Ritchie / Macefield: fusão quase completa acima de ~50 Hz em
   unidades rápidas.
3. **Força máxima do pool.** Recrutar tudo em alta frequência: a força deve tender a
   `maximumMuscleForce`.
4. **Linearidade da somação.** Duas unidades juntas versus a soma das isoladas — devem
   coincidir (o modelo é aditivo nas unidades, com saturação só *dentro* de cada uma).

## 8.4 `RaikovaMuscle` — `model/muscle/RaikovaMuscle.java`

**O que modela.** O **abalo de Raikova**.

**Fonte citada (literal, linhas 5-12):**

> "Músculo de unidades motoras com o abalo de **Raikova e Aladjov (2002)**: cada disparo
> soma `p·t^m·e^(−k·t)`, com k, p e m tirados do tempo de contração e do tempo de meia
> relaxação da unidade; a soma passa pela mesma saturação do SOCDS, com os parâmetros de
> Raikova.
>
> **Fiel ao ReMoto original: a cada passo os disparos da unidade são todos revisitados, e
> só contam enquanto o último lido tiver menos de 10 meias relaxações;** o comprimento fica
> em 1, como no SOCDS."

**Equações (construtor, linhas 43-50; `raikova`, linhas 150-152):**

```
k = ln2 / (−tc·ln(thr/tc) + thr − tc)
p = e^(−k·tc·(ln(tc) − 1))
m = k·tc
abalo(t) = p · t^m · e^(−k·t)
F = (2/(1 + e^(−bRaikova·Σabalos)) − 1) · twTetRaikova · gpeakRaikova
```

onde `tc = tpeakRaikova` (tempo de contração) e `thr = tHalfRaikova` (tempo de meia
relaxação) — ambos parâmetros **por unidade**, lidos de `MotorUnit`.

**Janela (linha 136):** só conta enquanto `t − tSpike < 10.0·tHalfRaikova`, onde
`tSpike` é o **próximo** disparo lido (ver F-19).

**Parâmetros.** `units`, `step` (ms). Por unidade: `tpeakRaikova`, `tHalfRaikova`,
`bRaikova`, `twTetRaikova`, `gpeakRaikova`.

**Status.** Implementado. Comprimento inoperante (fixo em 1).

**Como testar.**

1. **Forma do abalo.** Um disparo isolado: a curva deve ser `p·t^m·e^(−kt)`, com **pico
   exatamente em `tc = tpeakRaikova`** e amplitude 1 nesse ponto (é o que `p` normaliza),
   e com **meia relaxação em `thr = tHalfRaikova`**. Esses dois pontos são o teste
   quantitativo direto da derivação de k, p, m — e um bom teste unitário sem simulação.
2. **Comparação com SOCDS.** Mesmo pool, mesmos disparos, os dois modelos: o abalo de
   Raikova é **assimétrico** (sobe mais rápido do que relaxa), o alfa é menos. A diferença
   na força tetânica e na curva força-frequência é o observável.
3. **Custo.** O modelo revisita todos os disparos a cada passo: o tempo de simulação deve
   crescer quadraticamente com a duração (ver F-20) — vale um teste de desempenho.

## 8.5 `DistributionMomentsMuscle` — `model/muscle/DistributionMomentsMuscle.java`

**O que modela.** Momentos de distribuição. Javadoc (literal, linhas 5-11):

> "Músculo de momentos de distribuição: a ativação de cada tipo de unidade (lenta e
> rápida) é a soma, em todos os disparos, de `(e^(−t/τ1) − e^(−t/τ2))·(τ1 − τ2)`, com
> τ1 = 20 ms e τ2 = 15 ms.
>
> **Como no original, o modelo nunca chegou a calcular força: a força do músculo fica em 0,
> e só a ativação e o estado das unidades avançam.**"

**Parâmetros.** `TAU1 = 20` ms, `TAU2 = 15` ms (constantes, linhas 14-15); `units`.

**Fonte citada.** **Nenhuma** (ver F-21).

**Status.** **Inoperante** para força (declarado no javadoc); implementado apenas para
`activationNorm`, `activationNormSType`, `activationNormFType`.

**Como testar.** Confirmar que a força permanece 0 (teste de documentação do limite) e que
a ativação é a soma declarada. O método `activation(t, slow)` **avança o estado das
unidades a cada chamada** — um teste tem de cobrir esse efeito colateral (F-22).

## 8.6 `HillMuscle` — `model/muscle/HillMuscle.java` (404 linhas) ⭐

**O que modela.** O **músculo de Hill**: elemento contrátil com relações
força-comprimento e força-velocidade **separadas para fibras lentas (S) e rápidas (F)**,
elemento elástico paralelo, viscosidade, tendão em série e ângulo de penação.

**Javadoc (literal, linhas 8-21):**

> "Músculo do tipo Hill: elemento contrátil com relações força-comprimento e
> força-velocidade separadas para fibras lentas (S) e rápidas (F), elemento elástico
> paralelo e viscosidade, em série com o tendão e com ângulo de penação; a ativação de cada
> tipo vem dos abalos das unidades motoras, normalizada pela força máxima.
>
> Fiel ao ReMoto original:
>
> - **a dinâmica calcula os quatro estágios do Runge-Kutta, mas avança por Euler (só k1);
>   como cada estágio grava força e aceleração, ficam as do 4º estágio;**
> - **a cada passo cria um gerador derivado de `Rng` que não usa (o sorteio muda a
>   sequência de aleatoriedade do resto da simulação);**
> - o comprimento inicial é fixo por músculo (tornozelo a 3 graus)."

**Constantes do modelo (linhas 25-49) — todas sem citação de fonte (F-23):**

| Constante | Valor | Papel |
| --- | --- | --- |
| `EPS0_M` | 0.50 | deformação de referência do elemento paralelo |
| `MAXIMUM_VELOCITY_S` | **−7.88** | velocidade máxima de encurtamento, fibras lentas |
| `MAXIMUM_VELOCITY_F` | **−9.15** | idem, fibras rápidas |
| `AV0_S, AV1_S, AV2_S` | −4.7, 8.41, −5.34 | excêntrico, lentas |
| `BV_S` | 0.35 | idem |
| `AV0_F, AV1_F, AV2_F` | −1.53, **0**, **0** | excêntrico, rápidas |
| `BV_F` | 0.69 | idem |
| `CV0_S, CV1_S` | 5.88, **0** | concêntrico, lentas |
| `CV0_F, CV1_F` | −5.70, 9.18 | concêntrico, rápidas |
| `B_S, W_S, P_S` | 2.3, 1.12, 1.62 | força-comprimento, lentas |
| `B_F, W_F, P_F` | 1.55, 0.75, 2.12 | força-comprimento, rápidas |

**Equações.**

Força-comprimento (linhas 248-254), uma gaussiana generalizada:

```
FL_S(Ln) = e^(−|((Ln^2.3) − 1)/1.12|^1.62)
FL_F(Ln) = e^(−|((Ln^1.55) − 1)/0.75|^2.12)
```

Força-velocidade (linhas 256-279), com o **limiar em `v > 0.1`** separando os ramos:

```
se v > 0.1  (alongamento, excêntrico):
    FV = (Bv − (Av0 + Av1·Ln + Av2·Ln²)·v) / (Bv + v)
senão       (encurtamento, concêntrico):
    FV = (Vmax − v) / (Vmax + v·(Cv0 + Cv1·Ln))
```

Elemento paralelo (linhas 222-225) e viscoso (227-230):

```
F_paralelo = e^(skP·(Ln − 1)/EPS0_M) / e^(skP)
F_viscoso  = sdP · v
```

Força do elemento contrátil (linhas 281-288):

```
F_norm = F_paralelo + F_viscoso + a_S·FL_S·FV_S + a_F·FL_F·FV_F
F      = F_norm · maximumMuscleForce
```

Dinâmica (linhas 290-315):

```
penação:      α = asin(sin(α₀)/Ln)
aceleração:   a = (F_tendão·cos α − F·cos²α) / massa
integração:   length += step·velocity;  velocity += step·acceleration   [Euler]
```

Ativação (linhas 162-220):

```
f_unidade = AlphaTwitch.value(iteração)
contrib   = (2/(1 + e^(−b·f)) − 1)·twTet·gpeak
a_S = Σ_{tipo=S} contrib / Σ_todas(gpeak·twTet)
a_F = Σ_{tipo≠S} contrib / Σ_todas(gpeak·twTet)
```

**Parâmetros (record `Parameters`, linhas 51-55),** "Parâmetros de
HILL_TYPE_MUSCULOTENDON_PARAMETERS de um músculo":
`optimalLength`, `pennationAngle` (graus na entrada, convertido a rad na linha 116),
`slackTendonLength`, `maximumMuscleForce`, `muscleMass`, `viscosityCoeficient` (→ `sdP`),
`elasticCoeficientOfParallelElement` (→ `skP`).

**Comprimento inicial fixo por músculo (`setInitialLengthNorm`, linhas 133-147)** —
"tornozelo a 3 graus":

| Músculo | `lengthNorm` inicial |
| --- | --- |
| SOL | **0.97026** |
| MG | **1.00972** |
| LG | **1.04775** |
| TA | **1.23948** |
| outro | 1 |

**Status.** Implementado, com a integração degradada a Euler (documentada).

**Como testar.**

1. **Relação força-comprimento.** O observável clássico. Fixar a ativação em 1 e varrer
   `lengthNorm` de ~0,5 a ~1,5 com velocidade nula: a curva ativa deve ser uma sino com
   **pico em `Ln = 1`** (porque `Ln^b − 1 = 0` ali) e largura dada por `W`. Esperado:
   FL_S mais larga (W = 1,12) que FL_F (W = 0,75) — e isso é verificável diretamente da
   forma fechada, sem referência externa. Comparar o formato geral com a curva
   força-comprimento de sarcômero de Gordon, Huxley e Julian.
2. **Relação força-velocidade.** Ativação fixa, `Ln = 1`, varrer a velocidade: no ramo
   concêntrico a curva deve ser hiperbólica tipo Hill, chegando a zero em `Vmax`
   (−7,88 L₀/s para S, −9,15 para F); no excêntrico, subir até um platô de ~1,3-1,8 vezes
   a força isométrica. **Teste de descontinuidade obrigatório:** o ramo troca em
   `v = 0.1`, não em `v = 0` — medir se há salto na curva ali (F-24).
3. **Força passiva.** Ativação zero, varrer o comprimento: deve crescer
   exponencialmente. **Mas** em `Ln = 1` a expressão dá `e^(−skP)`, não zero — logo há
   força passiva de repouso em comprimento ótimo (F-25).
4. **Penação.** Varrer `Ln` e conferir `α = asin(sin(α₀)/Ln)`: espessura constante.
5. **Equilíbrio músculo-tendão.** Em regime isométrico, a força do músculo projetada e a
   do tendão devem se equilibrar — a aceleração deve ir a zero. Com Euler em vez de RK4,
   verificar se há deriva (F-26).
6. **Convergência do passo.** Reduzir `step` e medir a mudança na força de regime: com
   Euler o erro é de 1ª ordem, então a força deve mudar **linearmente** com o passo. Esse
   é o teste que quantifica o custo da degradação documentada.

## 8.7 `Tendon` — `model/muscle/Tendon.java`

**O que modela.** O **tendão não inervado em série** com o músculo de Hill.

**Javadoc (literal, linhas 3-10):**

> "Tendão não inervado em série com o músculo de Hill: o comprimento é o do musculotendão
> menos a projeção do músculo, e a força segue
> `cT·kT·ln(e^((L/L0 − LrT)/kT) + 1)` vezes a força máxima do músculo, com
> `L0 = 1,033 × comprimento de folga`.
>
> **Como no original, um distúrbio senoidal é calculado a cada passo e somado multiplicado
> por zero.**"

**Equações (`step`, linhas 41-49):**

```
L0T       = 1.033 · slackLength
distúrbio = 0.00005·sin(2π·20·t/1000)·(1 + 0.5·sin(2π·t/1000))     ← ×0
L         = L_musculotendão − L_músculo·cos(penação) + 0·distúrbio
Ln        = L / L0T
F_norm    = cT·kT·ln(e^((Ln − LrT)/kT) + 1)
F         = F_norm · muscleMaximumForce
```

**Parâmetros.** `muscleMaximumForce` (N), `slackLength` (m, "comprimento de folga do
tendão"), `cT`, `kT`, `lrT` (adimensionais da lei *softplus*).
No `step`: `t` (ms), `musculotendonLength` (m), `muscleLength` (m), `pennationAngle`
(rad).

**Fonte citada.** **Nenhuma** para a lei do tendão (ver F-27).

**Status.** Implementado. O distúrbio é **inoperante** (multiplicado por zero,
documentado).

**Como testar.**

1. **Curva força-deformação.** Varrer `Ln`: a lei *softplus* dá força ~0 abaixo de
   `LrT` (tendão frouxo) e aproximadamente linear acima, com rigidez `cT` para
   deformações grandes. Teste analítico fechado. Comparar com a curva
   tensão-deformação tendínea padrão (não linear até ~2% de deformação, linear depois).
2. **Equilíbrio em série.** Isometria: `F_tendão` deve igualar a projeção da força
   muscular.
3. **Fator 1,033.** Constante mágica sem justificativa no código (F-28) — vale um teste
   que a documente.

## 8.8 `BandPassFilter` — `model/emg/BandPassFilter.java`

**O que modela.** O **passa-faixa do EMG** da tela de resultados do ReMoto original
(filtro "1st2nd").

**Javadoc (literal, linhas 3-8):**

> "O passa-faixa do EMG da tela de resultados do ReMoto original (filtro '1st2nd'): um
> Butterworth passa-baixas de 2ª ordem em paralelo com uma seção de 1ª ordem,
> discretizados por invariância ao impulso. As contas são as do
> `ButterworthImpulseInvariance` original, com as mesmas expressões; só os vetores do
> tamanho do sinal viraram as duas amostras anteriores."

**Equações (construtor, linhas 28-45):**

```
ω1 = 2π·fc1;  ω2 = 2π·fc2;  D = √2·ω2·ω1 − ω1² − ω2²
α = −ω1·ω2²/D;  β = −ω2⁴/D;  γ = ω1·ω2²/D
ω0 = ω2/√2;  A = √2·ω2/2;  B = β√2/(α·ω2) − 1

a11 = −2·e^(−A·T)·cos(ω0·T);  a21 = e^(−2A·T)
b01 = T·α;  b11 = T·α·e^(−A·T)·(−cos(ω0·T) + B·sin(ω0·T))
a12 = −e^(−ω1·T);  b02 = T·γ

y1[n] = −a11·y1[n−1] − a21·y1[n−2] + b01·x[n] + b11·x[n−1]
y2[n] = −a12·y2[n−1] + b02·x[n]
saída = y1 + y2
```

**Parâmetros (javadoc, linhas 23-27):** `fc1` (Hz, "frequência de corte inferior"),
`fc2` (Hz, "superior"), `T` (**s**, "período de amostragem" — note: segundos, não ms).

**Status.** Implementado.

**Como testar.** Resposta em frequência: injetar senoides de frequência variável e medir
o ganho. A banda de passagem deve ficar entre `fc1` e `fc2`, com queda de ~6 dB/oitava
abaixo (1ª ordem) e ~12 dB/oitava acima (2ª ordem). Um teste de resposta ao impulso
comparado com a transformada inversa analítica fecha o caso. Com os cortes padrão de EMG
de superfície (**20-500 Hz**, SENIAM/Hermens), o sinal filtrado deve perder a componente
de movimento e manter o pico em 50-100 Hz.
**Atenção:** `D = √2·ω2·ω1 − ω1² − ω2²` zera quando `ω1/ω2` resolve
`x² − √2x + 1 = 0` — que não tem raiz real, logo `D < 0` sempre; mas `D → 0` se
`fc1 → fc2` não ocorre. Ainda assim, `fc1 = fc2` dá `D = (√2−2)ω²≠0`. Teste de borda:
`fc1 = 0` → `α = β = γ = 0` → filtro mudo (F-29).

## 8.9 `ThirdOrderBandPass` — `model/emg/ThirdOrderBandPass.java`

**O que modela.** O filtro "3rd order low – 3rd order high" do site antigo.

**Javadoc (literal, linhas 3-8):**

> "O filtro '3rd order low - 3rd order high' da tela de resultados do site antigo
> (`ButterworthBilinear`): um passa-baixa e um passa-alta de Butterworth de 3ª ordem em
> série, discretizados pela transformação bilinear com pré-distorção da frequência de
> corte. **As três primeiras amostras de cada estágio ficam em zero, como no original.**"

**Equações (`coefficients`, linhas 36-53):**

```
ωc  = 2π·fc
ωcw = (2/T)·tan(ωc·T/2)                    ← pré-distorção
a   = ωcw·T
a0 = 8 + 8a + 4a² + a³
a1 = −24 − 8a + 4a² + 3a³
a2 = 24 − 8a − 4a² + 3a³
a3 = −8 + 8a − 4a² + a³
d1 = −a1/a0;  d2 = −a2/a0;  d3 = −a3/a0

passa-alta:  c = (8, −24, 24, −8)/a0
passa-baixa: c0 = a³/a0;  c1 = c2 = 3a³/a0;  c3 = c0
```

Aplicação em série (linhas 22-34): passa-baixa em `fcHigh`, depois passa-alta em `fcLow`,
a partir de `i = 3`.

**Parâmetros (javadoc, linhas 17-21):** `x` (sinal), `fcLow` (Hz, "o do passa-alta"),
`fcHigh` (Hz, "o do passa-baixa"), `period` (**s**).

**Variantes selecionáveis.** Esta classe e `BandPassFilter` são as **duas variantes de
filtro de EMG** oferecidas (ver §9 para qual a configuração escolhe).

**Status.** Implementado. É **offline** (processa um vetor inteiro), ao contrário de
`BandPassFilter`, que é amostra a amostra — uma assimetria de interface (F-30).

**Como testar.** Resposta em frequência: ~18 dB/oitava em cada extremo (3ª ordem), e o
corte deve ficar **exatamente** em `fc` por causa da pré-distorção
(`ωcw = (2/T)tan(ωcT/2)`) — isso é testável diretamente e é a diferença em relação a uma
bilinear sem pré-distorção. Comparar os dois filtros no mesmo sinal: as fases diferem, e
isso muda a amplitude pico-a-pico das ondas M e H (relevante para a razão H/M medida).

---

# 9. Articulação e geometria — `br.remoto.core.model.joint`

## 9.1 `MusculotendonGeometry` — `model/joint/MusculotendonGeometry.java` ⭐

**O que modela.** O **comprimento do musculotendão e o braço de momento** no tornozelo,
por polinômios de 4º grau no ângulo.

**Javadoc (literal, linhas 3-11):**

> "Comprimento do musculotendão e braço de momento no tornozelo dos músculos do tríceps
> sural (SOL, MG, LG) e do tibial anterior (TA), por polinômios de 4º grau no ângulo do
> tornozelo (graus).
>
> Os do MG e do LG dependem do joelho: os coeficientes existem para joelho em 0 e em 90;
> com outro valor, comprimento e braço ficam 0. **Como no original, o ângulo do joelho
> chega aqui em radianos e é comparado com 0 e com 90, de modo que só o joelho estendido
> (0) tem coeficientes.** Para um músculo desconhecido, os valores anteriores ficam."

Esta é a admissão de divergência mais consequente do módulo (ver F-31).

**Equações.** `L(θ) = c4·θ⁴ + c3·θ³ + c2·θ² + c1·θ + c0` (m) e idem para o braço de
momento, com θ em **graus**. Os 12 conjuntos de coeficientes (linhas 24-135):

**Comprimento (m):**

| Músculo / joelho | c4 | c3 | c2 | c1 | c0 |
| --- | --- | --- | --- | --- | --- |
| SOL | 9.2739e−11 | −3.1478e−8 | −2.2431e−6 | 7.2186e−4 | **0.32284** |
| MG, joelho 0 | 7.3492e−11 | −3.4959e−8 | −1.1328e−6 | 7.4794e−4 | **0.46349** |
| MG, joelho 90 | 7.3209e−11 | −3.4982e−8 | −1.1266e−6 | 7.4750e−4 | **0.42343** |
| LG, joelho 0 | 7.6451e−11 | −3.5529e−8 | −1.2491e−6 | 7.6234e−4 | **0.45515** |
| LG, joelho 90 | 7.6452e−11 | −3.5540e−8 | −1.2478e−6 | 7.6228e−4 | **0.41375** |
| TA | 1.5002e−10 | +2.4220e−8 | −1.4106e−6 | **−7.4414e−4** | **0.30587** |

**Braço de momento (m):**

| Músculo / joelho | c4 | c3 | c2 | c1 | c0 |
| --- | --- | --- | --- | --- | --- |
| SOL | −5.4944e−11 | −2.2185e−8 | 5.4507e−6 | 2.5739e−4 | **−0.041363** |
| MG, joelho 0 | −1.0214e−10 | −1.8660e−8 | 6.0830e−6 | 1.3053e−4 | **−0.042864** |
| MG, joelho 90 | −1.0296e−10 | −1.8619e−8 | 6.0872e−6 | 1.2988e−4 | **−0.042841** |
| LG, joelho 0 | −1.0234e−10 | −1.9345e−8 | 6.1803e−6 | 1.4388e−4 | **−0.043687** |
| LG, joelho 90 | −1.0261e−10 | −1.9351e−8 | 6.1825e−6 | 1.4374e−4 | **−0.043684** |
| TA | −4.3391e−10 | −4.4462e−8 | −3.8880e−6 | 1.6568e−4 | **+0.042616** |

Note o sinal: os três plantiflexores têm braço de momento **negativo**, o TA
**positivo** — a convenção que faz o torque somar corretamente em
`força × braço de momento`.

**Fonte citada.** **Nenhuma.** Os polinômios são anônimos (ver F-32).

**Status.** **Parcial**: funcional só com o joelho estendido (`kneeAngle == 0`); com
qualquer outro valor, MG e LG devolvem 0.

**Como testar.**

1. **Valores em ângulo neutro.** `updateLength(0, 0)` deve dar exatamente os termos
   constantes: SOL 0,32284 m, MG 0,46349 m, LG 0,45515 m, TA 0,30587 m. Comparar com
   comprimentos musculotendíneos medidos para esses músculos (p.ex. os conjuntos
   antropométricos de Arnold et al. para o membro inferior) — valores da ordem de
   0,30-0,46 m são plausíveis.
2. **Braço de momento em neutro.** SOL ≈ −41,4 mm, MG ≈ −42,9 mm, LG ≈ −43,7 mm,
   TA ≈ +42,6 mm. Alvo externo: o braço de momento do tendão de Aquiles no tornozelo
   humano é **~40-55 mm**, e o do TA, **~35-45 mm**. Ótima concordância de ordem —
   e um teste de uma linha.
3. **Consistência L↔braço de momento.** O braço de momento deve ser (menos) a derivada do
   comprimento em relação ao ângulo: `r(θ) = −dL/dθ` (com θ em rad). Teste numérico
   direto: derivar o polinômio de comprimento, converter graus→rad, comparar com o
   polinômio do braço de momento. **É o teste de coerência mais valioso desta classe** e
   detecta se os dois conjuntos de coeficientes vieram do mesmo ajuste.
4. **Faixa de validade.** Varrer θ de −40° a +40° e conferir que os comprimentos ficam
   positivos e monótonos; os polinômios de 4º grau divergem fora da faixa de ajuste.
5. **O bug do joelho.** `updateLength(0, Math.PI/2)` deve devolver 0 para MG e LG —
   teste que documenta F-31.

## 9.2 `IsometricAnkle` — `model/joint/IsometricAnkle.java`

**O que modela.** O **tornozelo isométrico** e, com um sinal, o **tornozelo de ângulo
imposto**.

**Javadoc (literal, linhas 7-17):**

> "Tornozelo isométrico: o ângulo fica fixo e o torque é a soma, em cada músculo, da força
> vezes o braço de momento.
>
> Com um sinal de ângulo, é o tornozelo de ângulo imposto: o ângulo inicial mais o sinal,
> em graus, a cada passo (uma perturbação); o comprimento dos músculos e os fusos
> acompanham.
>
> **Fiel ao ReMoto original: o construtor sorteia um número (resto de um distúrbio
> aleatório desligado), e antes do primeiro passo o ângulo vale 0, de modo que os músculos
> veem o tornozelo em 0 no instante inicial.**"

**Equações (`step`, linhas 63-73):**

```
ângulo = angleSignal == null ? fixedAngle : fixedAngle + sinal(t)·π/180
torque = Σ_músculos (força · braço de momento)        [nulos não contam]
```

**Parâmetros.** `jointAngle` (**graus** na entrada, convertido a rad),
`kneeAngle` (graus → rad), `angleSignal` (`DoubleUnaryOperator`, graus em função de ms;
"null para o tornozelo isométrico").

**Interface `Actuator`** (linhas 20-28): `force()` (N) e `momentArm()` (m).

**Variantes selecionáveis.** Isométrico puro (construtor de 2 args, o padrão) ou com
ângulo imposto (3 args).

**Status.** Implementado.

**Como testar.**

1. **Torque isométrico voluntário.** Recrutar os plantiflexores progressivamente e medir
   o torque: o máximo deve ficar na ordem de **~100-150 N·m** para plantiflexão humana
   (compare com o `MAXIMUM_VOLUNTARY_TORQUE = 79` N·m de `PositionTaskAnkle`, §9.4 —
   F-33).
2. **Relação torque-ângulo.** Com `angleSignal` em rampa, medir o torque isométrico
   máximo em cada ângulo: deve seguir a composição de força-comprimento (§8.6) e braço de
   momento (§9.1), com pico perto do ângulo neutro.
3. **Resposta a perturbação.** Com `angleSignal` em degrau, o comprimento muda, os fusos
   disparam, e deve aparecer um **reflexo de estiramento** com latência curta. Observável:
   intervalo entre o degrau de ângulo e o aumento de EMG (~40-50 ms no sóleo humano, para
   o reflexo de latência média).
4. **Rotação em `t = 0`.** O javadoc avisa que o ângulo vale 0 antes do primeiro passo:
   teste que confirme o transitório inicial (F-34).

## 9.3 `InvertedPendulumAnkle` — `model/joint/InvertedPendulumAnkle.java` ⭐

**O que modela.** A **postura ereta como pêndulo invertido** no tornozelo.

**Javadoc (literal, linhas 7-16):**

> "Postura ereta como pêndulo invertido no tornozelo (60 kg, centro de massa a 0,85 m):
> torque gravitacional, torque passivo viscoelástico da articulação, o torque dos músculos
> (dobrado, pelas duas pernas) e um distúrbio gaussiano de 0,05 N·m. O ângulo só começa a
> ser integrado (Runge-Kutta de 4ª ordem, em segundos) depois de 1 s.
>
> **Fiel ao ReMoto original: os torques gravitacional e passivo de cada estágio do
> Runge-Kutta usam o ângulo e a velocidade do início do passo, e cada estágio sorteia o
> distúrbio com um gerador derivado de `Rng`. O ângulo do joelho fica em graus, ao
> contrário do tornozelo isométrico.**"

**Constantes (linhas 19-26) — sem citação de fonte (F-35):**

| Constante | Expressão | Valor |
| --- | --- | --- |
| `GRAVITY` | — | 9.81 m/s² |
| `BODY_MASS` | — | **60 kg** |
| `BODY_HEIGHT` | `0.50 · 1.70` | **0.85 m** (altura do centro de massa) |
| `BODY_INERTIA` | `(4/3)·m·h²` | ≈ 57.8 kg·m² (momento de um bastão uniforme sobre uma extremidade) |
| `JOINT_VISCOSITY` | — | **5.81 N·m·s/rad** |
| `JOINT_ELASTICITY` | `0.65·m·g·h` | ≈ 325.2 N·m/rad (**65% da rigidez gravitacional crítica**) |
| `REF_ANGLE` | `0·(π/180)` | 0 |
| `DISTURBANCE_STD` | — | **0.05 N·m** |

**Equações (`calculateAcceleration`, linhas 103-112):**

```
T_grav    = m·g·h·sin(θ)
T_passivo = B·ω + K·(θ − θ_ref)
distúrbio = 0.05 · N(0,1)                   [novo sorteio a cada estágio]
α = (T_grav + 2·T_músculos − T_passivo + distúrbio) / I
```

Centro de pressão (linhas 83-85):

```
CoM = h·sin(θ)
CoP = h·[sin(θ)·(1 + (I/(m·g·h))·ω²) − (I/(m·g·h))·cos(θ)·α]
```

E `jointTorque()` (linhas 64-67) devolve
`T_grav + 2·T_músculos − T_passivo + distúrbio`.

**Parâmetros.** `jointAngle` (graus), `kneeAngle` (**graus**, ao contrário do
isométrico), `step` (ms → s).

**Status.** Implementado, com a integração degradada (ver F-36: o "RK4" chama
`calculateAcceleration()` **sem argumentos**).

**Como testar.**

1. **Instabilidade sem controle.** Com os músculos desligados, o pêndulo deve **cair**:
   a rigidez passiva (325 N·m/rad) é menor que a gravitacional (`m·g·h` = 500 N·m/rad),
   então o equilíbrio é instável. É o teste de primeira ordem do modelo, e tem alvo
   analítico: o autovalor positivo é
   `λ = [−B + √(B² + 4I(mgh − K))]/(2I)`, com constante de tempo `1/λ`.
2. **Oscilação postural (*sway*).** Com o controle reflexo ativo, medir desvio-padrão do
   ângulo e do CoP. Alvos humanos em pé quieto: **CoP com ~5-10 mm de desvio-padrão** e
   espectro concentrado abaixo de 1 Hz.
3. **Relação CoP-CoM.** A expressão implementada é a relação clássica
   `CoP = CoM − (I/(mgh))·(d²CoM/dt²)` linearizada; teste numérico comparando as duas
   formas.
4. **Atraso de 1 s.** Até `t = 1000` ms o ângulo não é integrado: o teste tem de
   descartar o primeiro segundo.
5. **Consumo de aleatoriedade.** Quatro sorteios de `newRandom()` por passo: isso é o que
   acopla este modelo à reprodutibilidade de todo o resto (F-15).

## 9.4 `PositionTaskAnkle` — `model/joint/PositionTaskAnkle.java`

**O que modela.** A **tarefa de posição do pé**.

**Fonte citada (literal, linhas 7-16):**

> "Tarefa de posição do pé (**Maurer e Peterka, 2005**): o pé (2,01 kg a 0,052 m do
> tornozelo) contra uma fração da contração voluntária máxima (79 N·m), com rigidez e
> viscosidade da articulação. O ângulo só começa a ser integrado (Runge-Kutta de 4ª ordem,
> em segundos) depois de 1 s.
>
> **Fiel ao ReMoto original: o peso do pé entra multiplicado por zero, os torques de cada
> estágio do Runge-Kutta usam o ângulo e a velocidade do início do passo, e cada estágio
> cria um gerador derivado de `Rng` para um distúrbio de desvio-padrão zero.** O ângulo do
> joelho fica em graus."

**Constantes (linhas 19-28):**

| Constante | Valor |
| --- | --- |
| `GRAVITY` | 9.81 |
| `FOOT_MASS` | **2.01 kg** |
| `ANKLE_FOOT_DISTANCE` | **0.052 m** |
| `FOOT_INERTIA` | `(4/3)·2.01·0.052²` ≈ 0.00725 kg·m² |
| `JOINT_VISCOSITY` | **1.1 N·m·s/rad** |
| `JOINT_ELASTICITY` | **320 N·m/rad** |
| `MAXIMUM_VOLUNTARY_TORQUE` | **79 N·m** |
| `LEG_ANGLE` | 90° em rad |
| `REF_ANGLE` | 0 |
| `DISTURBANCE_STD` | **0** |

**Equações (`calculateAcceleration`, linhas 109-119):**

```
T_grav    = 0·(m_pé·g·d·sin(90° − θ)) − %MVC·79·cos(θ)    ← o peso do pé é anulado
T_passivo = 1.1·ω + 320·(θ − 0)
distúrbio = 0 · N(0,1)
α = (T_músculos − T_grav − T_passivo + distúrbio) / I_pé
```

`jointTorque()` (linhas 70-73) devolve `α · I_pé` — **definição diferente** da do pêndulo
invertido (F-37).

**Parâmetros.** `jointAngle` (graus), `kneeAngle` (graus), `percentageOfMVC`
("fração da contração voluntária máxima"), `step` (ms → s).

**Status.** **Parcial**: o termo do peso do pé é inoperante (×0) e o distúrbio também
(σ = 0), ambos documentados.

**Como testar.**

1. **Equilíbrio contra %MVC.** Em regime, o torque muscular deve igualar
   `%MVC · 79 · cos(θ) + 320·θ`. Teste quantitativo direto: varrer `percentageOfMVC` e
   medir o torque e o ângulo de equilíbrio.
2. **Resposta em frequência da articulação.** Com `K = 320` e `I = 0.00725`, a frequência
   natural é `√(K/I)/(2π)` ≈ **33,4 Hz**, e o amortecimento `B/(2√(KI))` ≈ 0,36 —
   subamortecido. Um degrau de torque deve oscilar nessa frequência. **Esse é um alvo
   analítico exato e um excelente teste**; se a simulação não mostrar ~33 Hz, a
   integração (F-36) é a suspeita.
3. **Comparação com Maurer e Peterka (2005).** Os parâmetros são atribuídos a essa fonte:
   comparar `FOOT_MASS`, `ANKLE_FOOT_DISTANCE`, `JOINT_ELASTICITY` e
   `JOINT_VISCOSITY` com a tabela do artigo é um teste de proveniência que vale a pena
   fazer manualmente uma vez.
