package br.remoto.core.model.signal;

import br.remoto.core.model.random.Draws;
import br.remoto.core.model.random.Rng;

/**
 * Sinal que modula um comando descendente, uma corrente injetada, a estimulação de um nervo ou uma
 * entrada biomecânica. Vale 0 fora da janela {@code [tini, tfin + T_TOLERANCE]} (ms).
 *
 * <p>As expressões são as do ReMoto original, com as particularidades de algumas formas:
 * <ul>
 *   <li>a senoide perde 7 depois de 120 s;</li>
 *   <li>a forma "custom" é a quadrada com amplitude fixa, 12,35 nos primeiros 10 s depois de
 *   {@code tini} e 13,75 depois disso, qualquer que seja {@code amp}. O original gravava essa
 *   amplitude de volta no sinal; aqui o sinal não muda, e o valor calculado é o mesmo;</li>
 *   <li>as formas aleatória e gama sorteiam a cada avaliação, em {@link Rng}.</li>
 * </ul>
 *
 * @param freq frequência (Hz) da senoide, da AM e da quadrada
 * @param width largura (ms) do pulso da quadrada e das rampas; na gama, a ordem
 * @param delay atraso (ms) da senoide e da AM
 * @param modFreq frequência (Hz) da modulante da AM
 * @param modFactor profundidade da modulação da AM
 */
public record Modulation(Shape shape, double tini, double tfin, double amp, double freq,
        double width, double delay, double modFreq, double modFactor) {

    /** Folga nas comparações de tempo (ms), a mesma do original. */
    public static final double T_TOLERANCE = 0.001;

    /** As formas do sinal, pelos códigos da configuração. */
    public enum Shape {
        NONE("none"),
        CONSTANT("constant"),
        SINE("sine"),
        SQUARE("square"),
        RAMP("ramp"),
        CUSTOM("custom"),
        RANDOM("random"),
        TRIANGLE("triangle"),
        TRAPEZOID("trapezoid"),
        STOCHASTIC_GAMMA("stochastic"),
        AM("AM"),
        /**
         * Sinal sem código (nulo na base). Como no original, vale 0 fora da janela e falha ao ser
         * avaliado dentro dela.
         */
        UNDEFINED(null);

        private final String code;

        Shape(String code) {
            this.code = code;
        }

        /** O código desta forma na configuração; null para {@link #UNDEFINED}. */
        public String code() {
            return code;
        }

        /** A forma de um código da configuração; um código desconhecido não modula (vale 0). */
        public static Shape of(String code) {
            if (code == null) {
                return UNDEFINED;
            }
            for (Shape shape : values()) {
                if (code.equals(shape.code)) {
                    return shape;
                }
            }
            return NONE;
        }
    }

    public Modulation {
        if (shape == null) {
            throw new IllegalArgumentException("forma do sinal nula: use Shape.UNDEFINED");
        }
    }

    /** Valor do sinal em {@code time} (ms). */
    public double value(double time) {
        if (time < tini || time > tfin + T_TOLERANCE) {
            return 0;
        }
        return switch (shape) {
            case NONE -> 0;
            case CONSTANT -> amp;
            case SINE -> sine(time);
            case SQUARE -> square(time, amp);
            case RAMP -> ramp(time);
            case CUSTOM -> square(time, time < 10000 + tini ? 12.35 : 13.75);
            case RANDOM -> amp * Rng.random();
            case TRIANGLE -> triangle(time);
            case TRAPEZOID -> trapezoid(time);
            case STOCHASTIC_GAMMA -> Draws.gamma(amp, width);
            case AM -> am(time);
            case UNDEFINED -> throw new IllegalStateException("sinal modulante sem código");
        };
    }

    private double sine(double time) {
        double ret = amp * Math.sin(2 * Math.PI * freq * time / 1000 + (2 * Math.PI * freq * -delay / 1000));
        if (time > 120000) {
            ret = ret - 7;
        }
        return ret;
    }

    private double am(double time) {
        return amp * Math.sin(2 * Math.PI * freq * time / 1000 + (2 * Math.PI * freq * -delay / 1000))
                * (1 + modFactor * Math.sin(2 * Math.PI * modFreq * time / 1000));
    }

    private double square(double time, double amplitude) {
        double period = 1000.0 / freq;
        int cycle = (int) ((time - tini) / period);
        double start = tini + cycle * period;
        if (time > start && time < start + width + T_TOLERANCE) {
            return amplitude;
        }
        return 0;
    }

    private double ramp(double time) {
        if (time - tini > width) {
            return amp;
        }
        return amp * (time - tini) / width;
    }

    private double triangle(double time) {
        if (time - tini < width) {
            return amp * (time - tini) / width;
        }
        return -amp * ((time - tini) / width) + 2 * amp;
    }

    private double trapezoid(double time) {
        if (time - tini <= width) {
            return amp * (time - tini) / width;
        } else if (time - tini > width && time - tini <= 2 * width) {
            return amp;
        }
        return -amp * ((time - tini) / (width)) + 3 * amp;
    }
}
