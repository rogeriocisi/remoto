package br.remoto.core.scenario;

/**
 * Núcleos motores simulados. Substitui as constantes {@code ReMoto.indSOL}, {@code ReMoto.indMG}...
 * do código legado. Novos núcleos (mão, punho) entram aqui quando o modelo for portado.
 */
public enum Nucleus {
    SOL("Sóleo"),
    MG("Gastrocnêmio medial"),
    LG("Gastrocnêmio lateral"),
    TA("Tibial anterior");

    private final String label;

    Nucleus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
