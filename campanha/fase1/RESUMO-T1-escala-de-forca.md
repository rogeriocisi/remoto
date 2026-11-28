# T1 — a escala de força: o que era, o que é, e o que ficou em aberto

Rodado em 28/11/2026.

## O que o teste original mediu, e por que estava errado

O primeiro T1 exigia `TWITCH_TENSION2 ≤ TETANIC_TENSION1`, linha a linha. Falhou nas **540
linhas de todos os 45 cenários**, por fatores de 1,8× a 5,1×. O invariante é fisiologia correta —
a força de abalo de uma unidade não pode exceder a sua tetânica — mas **essas duas colunas não são
esse par**:

- `TWITCH_TENSION` alimenta o pico de força de cada unidade em `JointFactory`;
- `TETANIC_TENSION` é lida do banco, devolvida ao banco e **nunca entra em cálculo de força**;
- a razão tetania/abalo que o modelo de fato usa é `twTet`, que estava **fixa no código** do
  `ConfigurationAssembler`: tríceps sural S 2,97→4,08, FR 4,08→6,71, FF 6,71→8,73; tibial
  anterior S 2,16→2,49. Valores fisiologicamente plausíveis, e em artigo nenhum.

O teste comparava uma coluna operante com uma coluna morta. Foi substituído.

## O teste novo, e o que ele achou

O invariante certo: a força máxima do pool (`Σ gpeak × twTet`, que é como
`CriticallyDampedMuscle` e `HillMuscle` a calculam) contra o `MAXIMUM_MUSCLE_FORCE` do
musculotendão de Hill do mesmo músculo. Falhou nos 180 casos, com padrão idêntico em todos os
cenários — e investigar o porquê rendeu o achado principal.

**Os dois modelos de músculo discordam da força máxima do mesmo músculo.**

- No **SOCDS**, `CriticallyDampedMuscle:53` define `maximumMuscleForce = Σ gpeak × twTet` e
  **nunca lê** o `MAXIMUM_MUSCLE_FORCE` da tabela. A força sai em newtons direto das unidades.
- No **Hill**, a mesma soma é só um denominador: `HillMuscle:217-218` normaliza a força do pool
  por ela e `:287` multiplica pelo `MAXIMUM_MUSCLE_FORCE` da tabela.

| músculo | máximo no SOCDS | máximo no Hill | razão |
|---|---|---|---|
| MG | 4.769 N | 1.306 N | **3,65×** |
| LG | 2.067 N | 606 N | **3,41×** |
| TA | 986 N | 674 N | 1,46× |
| SOL | 4.526 N | 3.586 N | 1,26× |

Trocar o modelo de músculo no menu da tela *Biomechanical Elements* reescala o eixo de força em
até 3,65×, em silêncio, **e com fator diferente por músculo** — não é nem um fator global que se
pudesse descontar.

Classificação: **pergunta para os autores**, não defeito a corrigir unilateralmente. Qual das duas
escalas é a pretendida é decisão de modelagem.

## O que foi feito

1. `twTet` e `b` saíram do código chumbado: viraram campos editáveis na tela *Muscle Units*
   ("Tetanus-to-twitch ratio" e "Saturation factor") e vão para o cenário na chave
   `motorUnitForce` de `settings`. Cenário sem a chave usa os valores de antes, então os 45
   existentes continuam idênticos bit a bit.
2. A força tetânica saiu dos campos editáveis. Continua no cenário, lida e devolvida como está —
   o editor só grava o que mostra —, e a nota da tela explica que nenhum cálculo de força a usa.
3. `docs/current-version.md` registra a escala de força real, diz que **a Tabela 3 de Cisi & Kohn
   2008 não a descreve mais**, e documenta a divergência entre os dois modelos.
4. O golden master do editor (3 entradas) foi regerado, com o motivo escrito no próprio arquivo.
   O golden master do motor não mudou.

## O que fica em aberto

- Qual escala de força é a pretendida, SOCDS ou Hill.
- Por que o desacordo é de 1,26× no sóleo e 3,65× no gastrocnêmio medial — se fosse um erro de
  conversão de unidade, seria o mesmo fator em todos.
- Se a coluna `TETANIC_TENSION`, que casa a Tabela 3 ao centésimo, é vestígio de uma
  parametrização anterior que foi substituída sem registro.

## Teste de regressão sugerido

Rodar o mesmo cenário com os dois modelos de músculo e exigir que a força de pico concorde dentro
de uma tolerância declarada. Hoje falharia por até 3,65×; é o teste que impede a divergência de
voltar depois que a decisão for tomada.
