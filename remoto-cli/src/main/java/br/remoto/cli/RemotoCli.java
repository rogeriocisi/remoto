package br.remoto.cli;

import br.remoto.core.engine.Engines;
import br.remoto.core.engine.placeholder.PlaceholderPoolEngine;
import br.remoto.legacy.LegacyEngine;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.List;

/**
 * ReMoto sem servidor: roda os mesmos cenários JSON do site, para scripts, cluster ou HPC.
 * Substitui os ~30 scripts {@code main()} copiados em br.remoto.teste do código legado.
 */
@Command(name = "remoto",
        mixinStandardHelpOptions = true,
        version = "ReMoto 3.0.0-SNAPSHOT",
        description = "Executa cenários do ReMoto sem servidor (mesmo JSON do site).",
        subcommands = {RunCommand.class, ValidateCommand.class, ExportLegacyCommand.class})
public final class RemotoCli implements Runnable {

    public static void main(String[] args) {
        System.exit(new CommandLine(new RemotoCli()).execute(args));
    }

    /** Os mesmos motores do site. */
    static Engines engines() {
        return new Engines(List.of(new LegacyEngine(), new PlaceholderPoolEngine()));
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
