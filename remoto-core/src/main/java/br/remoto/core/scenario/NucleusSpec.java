package br.remoto.core.scenario;

/**
 * Parâmetros de um núcleo motor no cenário.
 *
 * @param nucleus     qual núcleo
 * @param motoneurons tamanho do pool de motoneurônios
 * @param driveNa     comando descendente médio (nA)
 * @param noiseNa     desvio padrão do ruído do comando (nA)
 */
public record NucleusSpec(Nucleus nucleus, int motoneurons, double driveNa, double noiseNa) {
}
