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
 * <p>A forma "series", que o original não tinha (o ReMoto em Fortran aceita uma série de taxas
 * qualquer), é uma série de pontos (tempo em ms, valor) com interpolação linear; dois pontos no
 * mesmo tempo fazem um degrau. A janela vai do primeiro ao último ponto.
 *
 * @param freq frequência (Hz) da senoide, da AM e da quadrada
 * @param width largura (ms) do pulso da quadrada e das rampas; na gama, a ordem
 * @param delay atraso (ms) da senoide e da AM
 * @param modFreq frequência (Hz) da modulante da AM
 * @param modFactor profundidade da modulação da AM
 * @param series os pontos da forma "series", tempo e valor alternados ({@code t0, v0, t1, v1...});
 *     null nas outras formas
 */
public record Modulation(Shape shape, double tini, double tfin, double amp, double freq,
        double width, double delay, double modFreq, double modFactor, double[] series) {

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
        /** Série de pontos (tempo, valor), interpolada; não existia no original. */
        SERIES("series"),
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
        if (shape == Shape.SERIES) {
            checkSeries(series);
        }
    }

    /** As formas do original, sem série. */
    public Modulation(Shape shape, double tini, double tfin, double amp, double freq,
            double width, double delay, double modFreq, double modFactor) {
        this(shape, tini, tfin, amp, freq, width, delay, modFreq, modFactor, null);
    }

    /** Uma série de pontos ({@code t0, v0, t1, v1...}), com a janela do primeiro ao último. */
    public static Modulation series(double[] points) {
        checkSeries(points);
        return new Modulation(Shape.SERIES, points[0], points[points.length - 2], 0, 0, 0, 0, 0, 0,
                points.clone());
    }

    /**
     * Confere uma série: pelo menos dois pontos, números finitos e tempos que não voltam.
     *
     * @throws IllegalArgumentException se não for válida
     */
    public static void checkSeries(double[] points) {
        if (points == null || points.length < 4 || points.length % 2 != 0) {
            throw new IllegalArgumentException("a série precisa de pelo menos dois pontos (tempo e valor)");
        }
        for (int i = 0; i < points.length; i++) {
            if (!Double.isFinite(points[i])) {
                throw new IllegalArgumentException("a série tem um valor que não é número: " + points[i]);
            }
            if (i % 2 == 0 && i >= 2 && points[i] < points[i - 2]) {
                throw new IllegalArgumentException("os tempos da série não podem voltar: " + points[i - 2]
                        + " e depois " + points[i]);
            }
        }
    }

    /**
     * Lê uma série escrita como pontos separados por ";" ou quebra de linha, cada um com o tempo
     * (ms) e o valor separados por espaço ou ":" ("0 20; 1000 40"), com ponto decimal.
     */
    public static double[] parseSeries(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] pairs = text.trim().split("\\s*[;\\n]\\s*");
        double[] points = new double[pairs.length * 2];
        int n = 0;
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }
            String[] f = pair.trim().split("\\s*:\\s*|\\s+");
            if (f.length != 2) {
                throw new IllegalArgumentException("cada ponto da série é um tempo (ms) e um valor: \"" + pair + "\"");
            }
            try {
                points[n++] = Double.parseDouble(f[0]);
                points[n++] = Double.parseDouble(f[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("número inválido na série (use ponto decimal): \"" + pair + "\"");
            }
        }
        double[] result = java.util.Arrays.copyOf(points, n);
        checkSeries(result);
        return result;
    }

    /** A série no formato de {@link #parseSeries}. */
    public static String formatSeries(double[] points) {
        if (points == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < points.length; i += 2) {
            sb.append(i == 0 ? "" : "; ").append(number(points[i])).append(' ').append(number(points[i + 1]));
        }
        return sb.toString();
    }

    private static String number(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e15 ? Long.toString((long) v) : Double.toString(v);
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
            case SERIES -> interpolate(time);
            case UNDEFINED -> throw new IllegalStateException("sinal modulante sem código");
        };
    }

    private double interpolate(double time) {
        int n = series.length / 2;
        // o último ponto com tempo até time
        int lo = 0;
        int hi = n - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (series[2 * mid] <= time) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        if (lo == n - 1) {
            return series[2 * lo + 1];
        }
        double t0 = series[2 * lo];
        double t1 = series[2 * lo + 2];
        double v0 = series[2 * lo + 1];
        double v1 = series[2 * lo + 3];
        return t1 == t0 ? v1 : v0 + (v1 - v0) * (time - t0) / (t1 - t0);
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
