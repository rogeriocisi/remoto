package br.remoto.legacy.editor;

import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.setup.MiscellaneousVO;

/**
 * Pequenos "beans" para campos que, no site antigo, eram propriedades do formulário ligadas
 * direto à {@code Configuration} (e não a uma lista de VOs).
 */
public final class ConfAdapters {

    private ConfAdapters() {
    }

    /** start.jsp: "Time step [ms]" e "Duration [ms]". */
    public static final class SimulationSettings {
        private final Configuration conf;

        SimulationSettings(Configuration conf) {
            this.conf = conf;
        }

        public double getStep() {
            return conf.getStep();
        }

        public void setStep(double step) {
            conf.setStep(step);
        }

        public double getDuration() {
            return conf.getTFin();
        }

        public void setDuration(double duration) {
            conf.setTFin(duration);
        }

        @Override
        public String toString() {
            return "Simulation";
        }
    }

    /** network.jsp, "Anatomical position of spinal neurons": xIni&lt;pool&gt; / xEnd&lt;pool&gt; em MISCELLANEOUS. */
    public static final class PoolPosition {
        private final Configuration conf;
        private final String pool;
        private final String label;

        PoolPosition(Configuration conf, String pool, String label) {
            this.conf = conf;
            this.pool = pool;
            this.label = label;
        }

        static boolean exists(Configuration conf, String pool) {
            for (Object o : conf.getMiscellaneous()) {
                if (o instanceof MiscellaneousVO vo && ("xIni" + pool).equals(vo.getProperty())) {
                    return true;
                }
            }
            return false;
        }

        public double getInitial() {
            return conf.getMiscellaneous("xIni" + pool);
        }

        public void setInitial(double value) {
            conf.setMiscellaneous("xIni" + pool, value);
        }

        public double getFinal() {
            return conf.getMiscellaneous("xEnd" + pool);
        }

        public void setFinal(double value) {
            conf.setMiscellaneous("xEnd" + pool, value);
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
