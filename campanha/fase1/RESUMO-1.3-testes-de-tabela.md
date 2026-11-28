# Fase 1.3 — testes de tabela sobre os 45 cenários públicos

Rodado em 28/11/2026. Sem simulação: só coerência interna e aderência ao publicado.
Script: [`testes_de_tabela.py`](testes_de_tabela.py). Evidência: um JSON por teste nesta pasta.

| teste | veredito | falhas / itens | classificação |
|---|---|---|---|
| T1 abalo não excede tetânica | falha | **540 / 540** | defeito ou erro de documentação |
| T2 limiar de axônio aferente | falha | **360 / 360** | **não é defeito** — ver nota |
| T3 velocidades aferentes | falha | 7 conjuntos / 540 | divergência documentável |
| T4 contagem de motoneurônios | falha | 5 / 180 | **critério meu estrito demais** |
| T5 dinâmica sináptica coerente | falha | 137 / 272 | defeito provável |
| T6 exclusão Ia simétrica | falha | 270 / 810 | pergunta para os autores |
| T7 mecanismos inertes | falha | 2 / 135 | lacuna de cobertura |
| T8 limiar de interneurônio | falha | 3 tipos / 360 | defeito provável |

---

## T1 — a força de abalo excede a tetânica em todas as linhas de todos os cenários

Sistemático e idêntico em todo o repositório. Quatro padrões, repetidos 17 vezes cada:

| núcleo | tipo | `TWITCH_TENSION2` | `TETANIC_TENSION1` | razão |
|---|---|---|---|---|
| SOL | FF | 3,00 | 1,177 | 2,55× |
| SOL | FR | 2,50 | 0,491 | 5,09× |
| MG/LG/TA | FF | 2,15 | 1,177 | 1,83× |
| MG/LG/TA | FR | 1,90 | 0,491 | 3,87× |

A coluna tetânica casa a Tabela 3 de Cisi & Kohn 2008 ao centésimo (0,392 / 0,491 / 1,177 /
1,962 N = 40 / 50 / 120 / 200 gf) e **nunca entra em cálculo de força**: só é relida para a
tabela do editor. A coluna de abalo, que **alimenta o pico de força de cada unidade em
`JointFactory`**, não corresponde a tabela publicada nenhuma.

Como a uniformidade é total, isto não é erro de um cenário: ou as duas colunas estão em unidades
diferentes, ou `TWITCH_TENSION` deixou de ser força em newtons em algum momento e a documentação
não acompanhou. **Decisão do autor.** Enquanto não se resolver, nenhum teste de força pode usar a
Tabela 3 como alvo.

## T2 — limiar de axônio aferente: valor e direção divergem da Tabela 4

Dois padrões, em 100 linhas Ia e 100 Ib, idênticos em todos os cenários:

| fibra | banco | Cisi & Kohn 2008, Tabela 4 |
|---|---|---|
| Ia | 18 → 9 mA (decrescente) | 6,0 → 18,0 mA (crescente) |
| Ib | 16 → 10 mA (decrescente) | 13,0 → 22,0 mA (crescente) |

### Resolvido empiricamente — não é defeito

Rodei `ia880-h-reflex-conditioning` e li a tabela `tabela-aferentes-SOL`, que o próprio motor já
exporta com `velocidade_axonio[m/s]` e `limiar_axonio[mA]` por fibra. Os limiares atribuídos são
mesmo decrescentes (16,7 → 9,0 mA), isto é, a tabela é consumida literalmente. Mas:

| índice | velocidade (m/s) | limiar (mA) |
|---|---|---|
| 0 | 62,00 | 16,73 |
| 200 | 64,51 | 12,66 |
| 399 | 67,00 | 8,96 |

**Correlação velocidade × limiar = −0,965.** Os aferentes mais rápidos são os de menor limiar
elétrico, que é exatamente a fisiologia do axônio: diâmetro maior significa mais rápido **e** mais
fácil de excitar. A direção "decrescente" da tabela é a convenção de índice que emparelha com o
gradiente de velocidade — o artigo provavelmente numera a população no sentido oposto.

Isso também explica por que a curva H/M que medi em 27/11 sai canônica.

**O que sobra como divergência real:** a faixa. O banco dá Ia ≈ 8–20 mA onde o artigo publica
6–18, e Ib 10–16 onde o artigo publica 13–22. Para o Ia a diferença é um deslocamento de ~2 mA;
para o Ib é substancial. Divergência de parametrização, não de modelo.

**Ganho colateral:** este teste vira um invariante permanente para a suíte, e ele **passa hoje** —
*dentro de uma população aferente, velocidade de condução e limiar elétrico devem ter correlação
negativa forte (r ≤ −0,9)*. É barato, vale em qualquer cenário, e pegaria na hora qualquer troca
acidental de sinal num gradiente.

## T3 — três conjuntos de velocidade de condução aferente em uso

| fibra | faixa | cenários |
|---|---|---|
| Ia | 62–67 m/s | 32 |
| Ia | 55–66 m/s | 14 |
| Ia | 66–71 m/s | 1 |
| Ib | 56–62 m/s | 31 |
| Ib | 47–63 m/s | 14 |
| II | 30–35 m/s | 31 |
| II | 33–40 m/s | 14 |

O conjunto dominante é 62–67 / 56–62 / 30–35. Nenhum cenário declara de qual fonte vem o seu.
Não é defeito — é falta de proveniência, e impede validar latência contra qualquer artigo.

## T4 — critério meu estrito demais

As cinco divergências são todas deliberadas e explicáveis: `projectfr-antidromic-stimulation`
(SOL com 5 motoneurônios) e `projectfr-node-axons` (3) reduzem o núcleo de propósito para
demonstrar um elemento isolado; `remoto-38` e `remoto-44` usam LG com 1; e
`remoto-19-biceps-brachii-stim-ia` é outro conjunto muscular. **O teste é que estava errado**, não
o banco. Mantido como inventário.

## T5 — dinâmica sináptica aplicada pela metade

Dois padrões distintos:

- **`MN *-IN RC` (motoneurônio → célula de Renshaw), em 33 cenários.** O lado extensor
  (`IN_ext` ← LG, MG, SOL) está com `DYNAMIC_TYPE = 'N'`, sem dinâmica; o lado flexor
  (`IN_flex` ← TA) está com `'D'`. **E os dois guardam os mesmos τ = 200 ms e variação = 50%** —
  isto é, os parâmetros da depressão estão gravados nos dois lados e ligados só num.
- **`AF Ia-MN *`, em 12 a 14 cenários:** a exceção no par homônimo SOL←SOL.

Parâmetro gravado e desligado, só de um lado, é a assinatura de mudança aplicada pela metade.

## T6 — exclusão Ia unidirecional entre MG e SOL

Um único padrão, em ~67 ocorrências: `SOL←MG` em 0% e `MG←SOL` em 15%, para os três tipos de
motoneurônio. Conexões Ia heterônimas assimétricas existem na literatura, então **isto pode ser
deliberado** — mas não está escrito em lugar nenhum. Pergunta para os autores.

## T7 — dois mecanismos implementados e inertes em todos os 45 cenários

- `gammaCa = 0` → a corrente persistente de entrada nunca é exercitada. Elias et al. 2012 e 2013
  são irreproduzíveis por construção, e a biestabilidade que o MyoGen demonstra não aparece.
- `axonVelocitySTD = 0` → sem dispersão de velocidade de condução. Oliveira et al. 2022 exige
  desvio-padrão de 3,3 m/s, e é ele que determina a duração do CMAP nos seis cenários `aidp-*`.

Código implementado que nenhum cenário público exercita é código não validado.

## T8 — limiar de interneurônio: duas faixas para o mesmo tipo

| tipo | faixas em uso |
|---|---|
| IaIn | 10–15 mV e 10–20 mV |
| IbIn | 10–15 mV e 10–20 mV |
| gII | 10–15 mV e 10–20 mV |
| RC | 5–15 mV (consistente) |

Como 45 + 31 excede o número de cenários, as duas faixas convivem **dentro do mesmo cenário** —
extensor e flexor com limiares diferentes para o mesmo tipo de interneurônio. É o mesmo padrão de
T5: atualização aplicada a um lado só.

---

## O que isto muda no plano

Três achados (T1, T5, T8) apontam para a mesma coisa: **o banco recebeu atualizações aplicadas pela
metade, e nada no projeto detecta isso.** O conserto estrutural não é corrigir linha a linha — é
exigir que cada cenário **declare a sua fonte** e que um teste confira o conjunto inteiro contra
ela. Esse teste não existe e é barato.

Nenhum destes oito justifica tocar no motor agora. T2 precisa de leitura de código antes de
qualquer coisa; T1 e T6 precisam de decisão do autor.
