package br.remoto.web.api;

import br.remoto.core.engine.Engines;
import br.remoto.core.engine.legacy.LegacyJson;
import br.remoto.core.scenario.Scenario;
import br.remoto.core.scenario.ScenarioValidator;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.UserId;
import br.remoto.storage.scenario.ScenarioRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Cenários como arquivos, para guardá-los fora do site (não há contas): um .json por cenário, o
 * mesmo que a CLI roda, ou vários num .zip. A importação confere cada cenário como o editor e a
 * fila conferem, e põe os válidos na área da sessão.
 */
@Component
public class ScenarioFiles {

    /** Arquivos .json aceitos numa importação, somando os de dentro dos .zip. */
    static final int MAX_SCENARIOS = 100;
    /** Tamanho máximo de um cenário descompactado (um cenário do banco antigo tem ~140 kB). */
    static final int MAX_SCENARIO_BYTES = 5 * 1024 * 1024;

    /** O que entrou e o que foi recusado, com o nome do arquivo em cada erro. */
    public record Imported(List<Scenario> created, List<String> errors) {
    }

    private final JsonCodec json;
    private final ScenarioRepository scenarios;
    private final Engines engines;

    ScenarioFiles(JsonCodec json, ScenarioRepository scenarios, Engines engines) {
        this.json = json;
        this.scenarios = scenarios;
        this.engines = engines;
    }

    /** O cenário como arquivo: sem o id, que é da área de quem exportou. */
    public byte[] toJson(Scenario scenario) {
        return json.toBytes(scenario.withId(null));
    }

    /** "Default - Soleus contraction" → "default-soleus-contraction.json". */
    public static String fileName(Scenario scenario) {
        String ascii = Normalizer.normalize(scenario.name() == null ? "" : scenario.name(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (ascii.length() > 60) {
            ascii = ascii.substring(0, 60).replaceAll("-$", "");
        }
        return (ascii.isEmpty() ? "cenario" : ascii) + ".json";
    }

    /** Vários cenários num .zip, um .json cada, sem nomes repetidos. */
    public void writeZip(List<Scenario> list, OutputStream out) {
        Set<String> used = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Scenario s : list) {
                String base = fileName(s);
                String name = base;
                for (int n = 2; !used.add(name); n++) {
                    name = base.substring(0, base.length() - ".json".length()) + "-" + n + ".json";
                }
                zip.putNextEntry(new ZipEntry(name));
                zip.write(toJson(s));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Importa arquivos .json e .zip (com .json dentro) para a área de {@code owner}. */
    public Imported importFiles(UserId owner, List<MultipartFile> files) {
        List<Scenario> created = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int[] count = {0};
        for (MultipartFile file : files == null ? List.<MultipartFile>of() : files) {
            String name = file.getOriginalFilename() == null ? "arquivo" : file.getOriginalFilename();
            if (file.isEmpty()) {
                continue;
            }
            try (InputStream in = file.getInputStream()) {
                if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    importZip(owner, name, in, count, created, errors);
                } else {
                    importOne(owner, name, readLimited(in), count, created, errors);
                }
            } catch (IOException | IllegalStateException e) {
                errors.add(name + ": não foi possível ler (" + e.getMessage() + ")");
            }
        }
        if (created.isEmpty() && errors.isEmpty()) {
            errors.add("nenhum arquivo enviado");
        }
        return new Imported(List.copyOf(created), List.copyOf(errors));
    }

    private void importZip(UserId owner, String zipName, InputStream in, int[] count,
                           List<Scenario> created, List<String> errors) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                String entryName = entry.getName();
                String base = entryName.substring(entryName.lastIndexOf('/') + 1);
                if (entry.isDirectory() || base.startsWith(".") || entryName.startsWith("__MACOSX/")
                        || !base.toLowerCase(Locale.ROOT).endsWith(".json")) {
                    continue;
                }
                importOne(owner, zipName + "/" + entryName, readLimited(zip), count, created, errors);
            }
        }
    }

    private void importOne(UserId owner, String name, byte[] content, int[] count,
                           List<Scenario> created, List<String> errors) {
        if (++count[0] > MAX_SCENARIOS) {
            if (count[0] == MAX_SCENARIOS + 1) {
                errors.add("no máximo " + MAX_SCENARIOS + " cenários por importação; os demais foram ignorados");
            }
            return;
        }
        Scenario scenario;
        try {
            scenario = json.fromBytes(content, Scenario.class);
        } catch (RuntimeException e) {
            errors.add(name + ": não é um cenário do ReMoto em JSON");
            return;
        }
        List<String> problems = new ArrayList<>(ScenarioValidator.validate(scenario));
        if (problems.isEmpty()) {
            try {
                engines.forScenario(scenario);
                LegacyJson.toConfig(scenario.legacy());
            } catch (RuntimeException e) {
                problems.add(e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            errors.add(name + ": " + String.join("; ", problems));
            return;
        }
        created.add(scenarios.create(owner, scenario.withId(null)).value());
    }

    /** Lê até {@link #MAX_SCENARIO_BYTES}; um arquivo maior não é um cenário (nem uma bomba de zip). */
    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) > 0; ) {
            if (out.size() + n > MAX_SCENARIO_BYTES) {
                throw new IllegalStateException("maior que " + MAX_SCENARIO_BYTES / (1024 * 1024) + " MB");
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
