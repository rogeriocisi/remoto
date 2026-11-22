package br.remoto.legacy.editor;

import br.remoto.legacy.LegacyConfig;
import br.remoto.legacy.LegacyConfigurations;
import br.remoto.core.simulation.setup.Configuration;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Editor de cenários do motor original, no mesmo modelo do site antigo: a configuração é
 * carregada como {@code Configuration}, editada tela a tela pelas mesmas propriedades dos JSPs, e
 * gravada de volta nas tabelas do cenário como o {@code ConfigurationDAO.saveConfiguration}
 * original as gravava, sem banco de dados.
 */
public final class LegacyEditor {

    /** Resultado de uma gravação: as tabelas novas e a duração/passo que a configuração passou a ter. */
    public record Saved(LegacyConfig config, double durationMs, double stepMs) {
    }

    private final LegacyConfig original;
    private final Configuration conf;

    private LegacyEditor(LegacyConfig original) {
        this.original = original;
        this.conf = LegacyConfigurations.load(original);
    }

    /**
     * @param durationMs duração atual do cenário (o cenário manda; a tabela CONFIGURATION é só a origem)
     * @param stepMs     passo atual do cenário
     */
    public static LegacyEditor open(LegacyConfig config, double durationMs, double stepMs) {
        LegacyEditor editor = new LegacyEditor(config);
        editor.conf.setTFin(durationMs);
        editor.conf.setStep(stepMs);
        return editor;
    }

    public ScreenView view(String screenId, Map<String, String> selection) {
        return screen(screenId).view(conf, selection);
    }

    /** @return erros de validação; vazio quando a tela foi aplicada */
    public List<String> apply(String screenId, Map<String, String> selection, Map<String, String> form) {
        return screen(screenId).apply(conf, selection, form);
    }

    public Saved save() {
        return new Saved(LegacyConfigurations.store(conf, original), conf.getTFin(), conf.getStep());
    }

    private static LegacyScreen screen(String id) {
        return LegacyScreens.find(id).orElseThrow(() -> new NoSuchElementException("tela desconhecida: " + id));
    }
}
