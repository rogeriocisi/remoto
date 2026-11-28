# -*- coding: utf-8 -*-
"""Fase 1.3 — testes de tabela sobre os 45 cenários públicos.

Não roda simulação. Confere coerência interna e aderência aos valores publicados.
Critérios declarados aqui, no código, antes de rodar. Escreve um JSON de evidência
por teste em campanha/fase1/ e imprime um resumo.
"""
import json, os, glob, sys, subprocess, datetime

RAIZ = r"C:\Claude\Psigama\remoto"
CEN = os.path.join(RAIZ, "scenarios")
OUT = os.path.join(RAIZ, "campanha", "fase1")

# ---------------------------------------------------------------- publicado
# Cisi & Kohn 2008, Tabela 4 (conferida no PDF pela crítica adversarial):
#   limiar de axônio aferente, CRESCENTE do primeiro ao último da população
TAB4 = {"Ia": (6.0, 18.0), "Ib": (13.0, 22.0)}
# Elias et al. 2014, Tabela 2: contagens de motoneurônio
MN_ELIAS2014 = {"SOL": (800, 50, 50), "MG": (300, 150, 150),
                "LG": (130, 65, 65), "TA": (250, 50, 50)}


def carrega():
    for f in sorted(glob.glob(os.path.join(CEN, "*.json"))):
        try:
            d = json.load(open(f, encoding="utf-8"))
        except Exception as e:
            print("ERRO lendo", f, e, file=sys.stderr)
            continue
        yield os.path.basename(f), d


def tab(d, nome):
    t = (d.get("legacy") or {}).get("tables", {}).get(nome)
    if not t:
        return [], []
    return t["columns"], t["rows"]


def linhas(d, nome):
    cols, rows = tab(d, nome)
    for r in rows:
        yield dict(zip(cols, r))


def commit():
    try:
        return subprocess.run(["git", "-C", RAIZ, "rev-parse", "HEAD"],
                              capture_output=True, text=True).stdout.strip()[:12]
    except Exception:
        return "?"


AGORA = datetime.datetime.now(datetime.timezone.utc).isoformat()
COMMIT = commit()
resultados = []


def evidencia(teste, criterio, alvo, falhas, total, limitacao, classificacao=None):
    v = "passa" if not falhas else "falha"
    ev = {
        "teste": teste, "declarado_em": AGORA, "commit": COMMIT,
        "criterio": {"observavel": criterio, "alvo": alvo,
                     "tolerancia": "igualdade exata (teste de tabela)",
                     "fonte": "ver campo alvo"},
        "medido": {"itens_conferidos": total, "falhas": len(falhas),
                   "detalhe": falhas[:200]},
        "veredito": v,
        "classificacao": classificacao or ("—" if v == "passa" else "a classificar"),
        "limitacao": limitacao,
    }
    json.dump(ev, open(os.path.join(OUT, teste + ".json"), "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    resultados.append((teste, v, len(falhas), total))
    return ev


# ---------------------------------------------------------------- T1
# Razão tetania/abalo por núcleo e tipo, espelhando ConfigurationAssembler
# (valores fixos no código; um cenário salvo pelo editor passa a trazê-los em
# settings.motorUnitForce, e então valem os do cenário).
TWTET_PADRAO = {
    "TA": {"S": (2.16, 2.49), "FR": (2.49, 2.49), "FF": (2.49, 2.49)},
    "*": {"S": (2.97, 4.08), "FR": (4.08, 6.71), "FF": (6.71, 8.73)},
}


def _twtet(nucleo, tipo, settings):
    chave = "%s:%s" % (nucleo, tipo)
    v = (settings or {}).get("motorUnitForce", {}).get(chave)
    if isinstance(v, list) and len(v) == 4:
        return float(v[0]), float(v[1])
    return TWTET_PADRAO.get(nucleo, TWTET_PADRAO["*"]).get(tipo, (1.0, 1.0))


def t1_forca_maxima_do_pool():
    """A força máxima que o pool de unidades motoras pode gerar contra a que o
    modelo de Hill declara para o mesmo músculo. São duas declarações
    independentes da mesma grandeza e deveriam concordar."""
    falhas, total = [], 0
    for nome, d in carrega():
        settings = (d.get("legacy") or {}).get("settings") or {}
        quant, mu, hill = {}, {}, {}
        for r in linhas(d, "NEURON_TYPE"):
            if r.get("CD_CATEGORY") == "MN":
                quant.setdefault(r["CD_NUCLEUS"], {})[r["CD_NEURON_TYPE"]] = r.get("QUANTITY") or 0
        for r in linhas(d, "MOTORUNIT_TYPE"):
            mu.setdefault(r["CD_NUCLEUS"], {})[r["CD_MOTORUNIT_TYPE"]] = r
        for r in linhas(d, "HILL_TYPE_MUSCULOTENDON_PARAMETERS"):
            hill[r["CD_MUSCLE"]] = r
        for nucleo in sorted(quant):
            fmax = (hill.get(nucleo) or {}).get("MAXIMUM_MUSCLE_FORCE")
            if not fmax or nucleo not in mu:
                continue
            soma, n = 0.0, 0
            for tipo in ("S", "FR", "FF"):
                k = quant[nucleo].get(tipo, 0)
                r = mu[nucleo].get(tipo)
                if not k or not r:
                    continue
                n += k
                a, b = _twtet(nucleo, tipo, settings)
                t1v, t2v = r.get("TWITCH_TENSION1"), r.get("TWITCH_TENSION2")
                if t1v is None or t2v is None:
                    continue
                for i in range(k):
                    f = i / (k - 1) if k > 1 else 0.0
                    soma += (t1v + f * (t2v - t1v)) * (a + f * (b - a))
            if n < 50:
                continue   # núcleo reduzido de propósito, não é caso de validar escala
            total += 1
            razao = soma / fmax
            if not (0.8 <= razao <= 1.25):
                falhas.append({"cenario": nome, "nucleo": nucleo, "n_unidades": n,
                               "soma_gpeak_twTet_N": round(soma, 1),
                               "fmax_hill_N": fmax, "razao": round(razao, 2)})
    return evidencia(
        "t1-forca-maxima-do-pool",
        "soma de gpeak*twTet sobre as unidades do núcleo contra MAXIMUM_MUSCLE_FORCE "
        "do musculotendão de Hill do mesmo músculo",
        "as duas são declarações independentes da força máxima do músculo e devem "
        "concordar dentro de 25%; é assim que CriticallyDampedMuscle e HillMuscle "
        "calculam o máximo do pool",
        falhas, total,
        "Substitui o teste anterior, que comparava TWITCH_TENSION com TETANIC_TENSION: "
        "a segunda nunca entra em cálculo de força, então a comparação não media nada "
        "do que a simulação faz. Este compara duas grandezas que o motor de fato usa. "
        "A tolerância de 25% é arbitrada por mim, não publicada: serve para separar "
        "discrepância de escala de diferença de arredondamento.")


# ---------------------------------------------------------------- T2
def t2_limiar_axonio_aferente():
    falhas, total = [], 0
    for nome, d in carrega():
        for r in linhas(d, "NEURON_TYPE"):
            tipo = r.get("CD_NEURON_TYPE")
            if tipo not in TAB4 or r.get("CD_CATEGORY") != "AF":
                continue
            total += 1
            a1, a2 = r.get("AXON_THRESHOLD1"), r.get("AXON_THRESHOLD2")
            esp1, esp2 = TAB4[tipo]
            prob = []
            if a1 is not None and a2 is not None and a1 > a2:
                prob.append("decrescente")
            if (a1, a2) != (esp1, esp2):
                prob.append("valores != Tabela 4")
            if prob:
                falhas.append({"cenario": nome, "nucleo": r.get("CD_NUCLEUS"),
                               "fibra": tipo, "banco": [a1, a2],
                               "publicado": [esp1, esp2], "problema": "+".join(prob)})
    return evidencia(
        "t2-limiar-axonio-aferente",
        "AXON_THRESHOLD1/2 das fibras Ia e Ib",
        "Cisi & Kohn 2008, Tabela 4: Ia 6,0 -> 18,0 mA e Ib 13,0 -> 22,0 mA, crescente",
        falhas, total,
        "Não prova qual dos dois está certo: o banco pode ter sido reparametrizado "
        "de propósito. Prova que o banco contradiz a tabela em valor E em direção.")


# ---------------------------------------------------------------- T3
def t3_velocidades_aferentes():
    conj, total = {}, 0
    for nome, d in carrega():
        for r in linhas(d, "NEURON_TYPE"):
            tipo = r.get("CD_NEURON_TYPE")
            if r.get("CD_CATEGORY") != "AF" or tipo not in ("Ia", "Ib", "II"):
                continue
            total += 1
            k = (tipo, r.get("AXON_VELOCITY1"), r.get("AXON_VELOCITY2"))
            conj.setdefault(k, set()).add(nome)
    falhas = []
    for tipo in ("Ia", "Ib", "II"):
        vistos = [(k, v) for k, v in conj.items() if k[0] == tipo]
        if len(vistos) > 1:
            for k, cen in sorted(vistos, key=lambda x: -len(x[1])):
                falhas.append({"fibra": tipo, "faixa": [k[1], k[2]],
                               "n_cenarios": len(cen),
                               "exemplos": sorted(cen)[:4]})
    return evidencia(
        "t3-velocidades-aferentes-divergem",
        "AXON_VELOCITY1/2 das fibras Ia, Ib e II em todos os cenários",
        "um único conjunto por tipo de fibra, declarado por fonte publicada",
        falhas, total,
        "Não diz qual conjunto é o certo. Diz que há mais de um em uso e que "
        "nenhum cenário declara qual fonte segue.")


# ---------------------------------------------------------------- T4
def t4_contagem_motoneuronios():
    falhas, total = [], 0
    for nome, d in carrega():
        q = {}
        for r in linhas(d, "NEURON_TYPE"):
            if r.get("CD_CATEGORY") != "MN":
                continue
            q.setdefault(r.get("CD_NUCLEUS"), {})[r.get("CD_NEURON_TYPE")] = r.get("QUANTITY")
        for nucleo, tipos in sorted(q.items()):
            if nucleo not in MN_ELIAS2014:
                continue
            total += 1
            atual = (tipos.get("S"), tipos.get("FR"), tipos.get("FF"))
            if any(x is None for x in atual):
                continue
            # núcleo desligado, ou reduzido de propósito para demonstrar um elemento
            # isolado (projectfr-node-axons tem 3 MNs): não é divergência de fonte.
            if not any(atual) or sum(atual) < 50:
                continue
            if tuple(atual) != MN_ELIAS2014[nucleo]:
                falhas.append({"cenario": nome, "nucleo": nucleo,
                               "banco": list(atual),
                               "elias2014": list(MN_ELIAS2014[nucleo])})
    return evidencia(
        "t4-contagem-motoneuronios",
        "QUANTITY das linhas de motoneurônio, por núcleo e tipo",
        "Elias et al. 2014, Tabela 2: SOL 800/50/50, MG 300/150/150, "
        "LG 130/65/65, TA 250/50/50",
        falhas, total,
        "Cenários com núcleo desligado aparecem com zeros e foram ignorados. "
        "Divergência aqui pode ser escolha do cenário, não defeito.")


# ---------------------------------------------------------------- T5
def t5_dinamica_sinaptica_coerente():
    falhas, total = [], 0
    for nome, d in carrega():
        por_tipo = {}
        for r in linhas(d, "SYNAPTIC_DYNAMICS"):
            por_tipo.setdefault(r.get("CD_CONDUCTANCE_TYPE"), []).append(r)
        for tipo, rs in sorted(por_tipo.items()):
            total += 1
            dt = {r.get("DYNAMIC_TYPE") for r in rs}
            if len(dt) > 1:
                excecoes = [{"pos": r.get("CD_NUCLEUS"), "pre": r.get("CD_NUCLEUS_PRE"),
                             "tipo_dinamica": r.get("DYNAMIC_TYPE"),
                             "tau": r.get("TAU"), "variacao": r.get("VARIATION")}
                            for r in rs]
                falhas.append({"cenario": nome, "conductance_type": tipo,
                               "tipos_presentes": sorted(dt), "linhas": excecoes})
    return evidencia(
        "t5-dinamica-sinaptica-coerente",
        "DYNAMIC_TYPE uniforme dentro de cada CD_CONDUCTANCE_TYPE",
        "uma via sináptica ou tem dinâmica (depressão/facilitação) ou não tem; "
        "a exceção por par de núcleos precisa de justificativa escrita",
        falhas, total,
        "Uma exceção pode ser deliberada. O teste aponta onde ela existe sem "
        "estar documentada.")


# ---------------------------------------------------------------- T6
def t6_exclusao_ia_simetrica():
    falhas, total = [], 0
    for nome, d in carrega():
        conn = {}
        for r in linhas(d, "CONDUCTANCE_TYPE"):
            ct = r.get("CD_CONDUCTANCE_TYPE") or ""
            if not ct.startswith("AF Ia-MN"):
                continue
            conn[(r.get("CD_NUCLEUS"), r.get("CD_NUCLEUS_PRE"), ct)] = r.get("CONNECTIVITY")
        for (pos, pre, ct), c in sorted(conn.items()):
            if pos == pre:
                continue
            total += 1
            inverso = conn.get((pre, pos, ct))
            if inverso is None:
                continue
            if (c == 0) != (inverso == 0):
                falhas.append({"cenario": nome, "via": ct,
                               "%s<-%s" % (pos, pre): c, "%s<-%s" % (pre, pos): inverso})
    return evidencia(
        "t6-exclusao-ia-simetrica",
        "conectividade Ia->MN entre núcleos diferentes, nos dois sentidos",
        "se uma projeção Ia entre dois núcleos é declarada ausente (0%), "
        "a recíproca também deve ser",
        falhas, total,
        "Assimetria anatômica real existe. O teste aponta onde ela é afirmada "
        "sem fonte.")


# ---------------------------------------------------------------- T7
def t7_mecanismos_desligados():
    vals, total = {}, 0
    for nome, d in carrega():
        for r in linhas(d, "MISCELLANEOUS"):
            p = r.get("PROPERTY")
            if p in ("axonVelocitySTD", "gammaCa", "axonThresholdSTD"):
                total += 1
                vals.setdefault(p, {}).setdefault(r.get("VALUE"), []).append(nome)
    falhas = []
    for p, porval in sorted(vals.items()):
        zero = porval.get(0, []) + porval.get(0.0, [])
        if zero:
            falhas.append({"propriedade": p, "valor": 0, "n_cenarios": len(zero),
                           "distribuicao": {str(k): len(v) for k, v in porval.items()}})
    return evidencia(
        "t7-mecanismos-implementados-e-desligados",
        "axonVelocitySTD, gammaCa e axonThresholdSTD em todos os cenários",
        "um mecanismo implementado e exercitado por nenhum cenário público é "
        "código não validado; gammaCa=0 torna Elias 2012/2013 irreproduzível e "
        "axonVelocitySTD=0 mata a dispersão de 3,3 m/s que Oliveira 2022 exige",
        falhas, total,
        "Valor zero pode ser a escolha correta do cenário. O teste mede quantos "
        "cenários deixam cada mecanismo inerte.")


# ---------------------------------------------------------------- T8
def t8_limiar_interneuronios():
    """Agrupa por TIPO de interneurônio: tipos diferentes podem ter limiares
    diferentes de propósito. Só é divergência quando o MESMO tipo tem faixas
    diferentes entre cenários."""
    vals, total = {}, 0
    for nome, d in carrega():
        for r in linhas(d, "NEURON_TYPE"):
            if r.get("CD_CATEGORY") != "IN":
                continue
            total += 1
            tipo = r.get("CD_NEURON_TYPE")
            vals.setdefault(tipo, {}).setdefault(
                (r.get("THRESHOLD1"), r.get("THRESHOLD2")), set()).add(nome)
    falhas = []
    for tipo, porfaixa in sorted(vals.items()):
        if len(porfaixa) > 1:
            falhas.append({"tipo": tipo,
                           "faixas": [{"faixa": list(k), "n_cenarios": len(v),
                                       "exemplos": sorted(v)[:3]}
                                      for k, v in sorted(porfaixa.items(),
                                                         key=lambda x: -len(x[1]))]})
    inventario = {t: [list(k) for k in p] for t, p in sorted(vals.items())}
    ev = evidencia(
        "t8-limiar-interneuronios",
        "THRESHOLD1/2 por TIPO de interneurônio (IaIn, IbIn, RC, gII)",
        "o mesmo tipo de interneurônio deve ter a mesma faixa em todos os "
        "cenários que declarem a mesma fonte; Elias et al. 2014 publica 10 a 20 mV",
        falhas, total,
        "Tipos diferentes com faixas diferentes é esperado e não conta como falha. "
        "A comparação com 10/20 exige confirmar a tabela no artigo.")
    ev["medido"]["inventario_por_tipo"] = inventario
    json.dump(ev, open(os.path.join(OUT, "t8-limiar-interneuronios.json"), "w",
                       encoding="utf-8"), ensure_ascii=False, indent=1)
    return ev


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    os.makedirs(OUT, exist_ok=True)
    for f in (t1_forca_maxima_do_pool, t2_limiar_axonio_aferente, t3_velocidades_aferentes,
              t4_contagem_motoneuronios, t5_dinamica_sinaptica_coerente,
              t6_exclusao_ia_simetrica, t7_mecanismos_desligados, t8_limiar_interneuronios):
        f()
    print("%-42s %-8s %8s %8s" % ("teste", "veredito", "falhas", "itens"))
    print("-" * 70)
    for t, v, nf, n in resultados:
        print("%-42s %-8s %8d %8d" % (t, v, nf, n))
    print()
    print("evidência em", OUT)
