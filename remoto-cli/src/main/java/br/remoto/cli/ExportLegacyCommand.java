package br.remoto.cli;

import br.remoto.core.scenario.Scenario;
import br.remoto.legacy.LegacyDatabase;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.StorageFiles;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * {@code remoto export-legacy --db WebContent/db/remoto --out scenarios} converte configurações
 * do banco HSQLDB do ReMoto original em cenários JSON da nova plataforma.
 */
@Command(name = "export-legacy",
        description = "Converte configurações do banco do ReMoto original em cenários JSON.")
final class ExportLegacyCommand implements Callable<Integer> {

    @Option(names = "--db", required = true,
            description = "banco legado, sem extensão (ex.: WebContent/db/remoto)")
    Path database;

    @Option(names = "--out", description = "pasta de saída (padrão: ./scenarios)")
    Path out = Path.of("scenarios");

    @Option(names = "--id", split = ",", description = "IDs das configurações (ex.: 17,24,42)")
    List<Integer> ids;

    @Option(names = "--owner", description = "só as configurações deste usuário do site antigo (ex.: guest)")
    String owner;

    @Option(names = "--list", description = "só lista as configurações, sem exportar")
    boolean list;

    @Override
    public Integer call() {
        JsonCodec json = new JsonCodec();
        try (LegacyDatabase db = LegacyDatabase.open(database)) {
            List<LegacyDatabase.ConfigurationInfo> selected = db.configurations().stream()
                    .filter(c -> ids == null || ids.contains(c.id()))
                    .filter(c -> owner == null || owner.equals(c.owner()))
                    .toList();
            for (LegacyDatabase.ConfigurationInfo info : selected) {
                if (list) {
                    System.out.printf("%4d  %-12s %8.0f ms  %s%n", info.id(), info.owner(), info.durationMs(), info.name());
                    continue;
                }
                Scenario scenario = db.exportScenario(info.id());
                Path file = out.resolve(slug(info.id(), info.name()) + ".json");
                StorageFiles.writeAtomically(file, json.toBytes(scenario));
                System.out.println(file);
            }
            if (selected.isEmpty()) {
                System.err.println("nenhuma configuração encontrada com esses filtros");
                return 2;
            }
            return 0;
        }
    }

    /** "JN - Watanabe and Kohn (2015)" → "remoto-42-jn-watanabe-and-kohn-2015". */
    static String slug(int id, String name) {
        String ascii = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        String base = "remoto-" + id + (ascii.isEmpty() ? "" : "-" + ascii);
        return base.length() > 64 ? base.substring(0, 64).replaceAll("-$", "") : base;
    }
}
