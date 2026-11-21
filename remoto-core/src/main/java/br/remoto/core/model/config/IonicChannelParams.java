package br.remoto.core.model.config;

/** Linha de MARKOV_TYPE: a cinética de um canal iônico ou sináptico. */
public record IonicChannelParams(
        String type,
        String nucleus,
        double tpeak,
        double tmax,
        double alpha,
        double beta,
        double gmax,
        double e,
        int index) {
}
