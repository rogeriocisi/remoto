package br.remoto.core.engine;

import br.remoto.core.scenario.Scenario;

/**
 * Motor de simulação. Implementações devem ser determinísticas: mesmo cenário + mesma seed +
 * mesma {@link #version()} produzem exatamente os mesmos resultados.
 */
public interface SimulationEngine {

    /** Identificador usado no campo {@code engine} do cenário (ex.: "legacy"). */
    String id();

    /** Versão do motor, gravada no manifesto de cada execução. */
    String version();

    /**
     * Executa o cenário na thread atual, publicando progresso e resultados no contexto.
     *
     * @throws SimulationCancelledException se {@link RunContext#isCancelled()} ficar verdadeiro
     */
    void run(Scenario scenario, RunContext context);
}
