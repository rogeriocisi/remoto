package br.remoto.core.model.receptor;

/**
 * Fuso muscular agrupado de Prochazka (1999): as taxas das aferências primária e secundária são
 * funções diretas do comprimento normalizado e da velocidade de estiramento, sem fusimotor.
 */
public final class ProchazkaSpindle {

    private double lengthNorm;
    private double stretchVelocity;

    private double iaFiringRate;
    private double iiFiringRate;

    /**
     * Avança o fuso um passo.
     *
     * @param fascicleLength comprimento normalizado do fascículo
     * @param velocity velocidade de estiramento (por ms)
     */
    public void step(double fascicleLength, double velocity) {
        lengthNorm = fascicleLength;
        stretchVelocity = velocity * 1000;

        iaFiringRate = calculateIaFiringRate();
        iiFiringRate = calculateIIFiringRate();
    }

    private double calculateIaFiringRate() {
        double output;
        double aux = Math.pow(Math.abs(stretchVelocity), 0.6);

        if (stretchVelocity >= 0) {
            output = 80 * aux + 250 * (lengthNorm - 1.00) + 15;
        } else {
            output = -80 * aux + 250 * (lengthNorm - 1.00) + 15;
        }

        if (output < 0) {
            output = 0;
        }

        return output;
    }

    private double calculateIIFiringRate() {
        double output = 250 * (lengthNorm - 1.00) + 8;

        if (output < 0) {
            output = 0;
        }

        return output;
    }

    /** Taxa da aferência primária (Hz) no último passo. */
    public double iaFiringRate() {
        return iaFiringRate;
    }

    /** Taxa da aferência secundária (Hz) no último passo. */
    public double iiFiringRate() {
        return iiFiringRate;
    }
}
