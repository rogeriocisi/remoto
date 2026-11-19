package br.remoto.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * ReMoto sem servidor: roda os mesmos cenários JSON do site, para scripts, cluster ou HPC.
 * Substitui os ~30 scripts {@code main()} copiados em br.remoto.teste do código legado.
 */
@Command(name = "remoto",
        mixinStandardHelpOptions = true,
        version = "ReMoto 3.0.0-SNAPSHOT",
        description = "Executa cenários do ReMoto sem servidor (mesmo JSON do site).",
        subcommands = {RunCommand.class, ValidateCommand.class})
public final class RemotoCli implements Runnable {

    public static void main(String[] args) {
        System.exit(new CommandLine(new RemotoCli()).execute(args));
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
