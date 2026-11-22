package br.remoto.web.results;

/** FFT radix-2 iterativa, no lugar (o {@code br.remoto.util.FFT} da tela antiga era a recursiva de Princeton). */
final class Fft {

    private Fft() {
    }

    /** @param re parte real; @param im parte imaginária; o tamanho é uma potência de 2 */
    static void transform(double[] re, double[] im) {
        int n = re.length;
        if (n != im.length || Integer.bitCount(n) != 1) {
            throw new IllegalArgumentException("tamanho deve ser potência de 2: " + n);
        }
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2 * Math.PI / len;
            double wRe = Math.cos(angle);
            double wIm = Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                double curRe = 1;
                double curIm = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k;
                    int b = a + len / 2;
                    double tRe = re[b] * curRe - im[b] * curIm;
                    double tIm = re[b] * curIm + im[b] * curRe;
                    re[b] = re[a] - tRe;
                    im[b] = im[a] - tIm;
                    re[a] += tRe;
                    im[a] += tIm;
                    double next = curRe * wRe - curIm * wIm;
                    curIm = curRe * wIm + curIm * wRe;
                    curRe = next;
                }
            }
        }
    }
}
