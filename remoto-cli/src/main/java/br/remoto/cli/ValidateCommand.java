package br.remoto.cli;

import br.remoto.core.scenario.Scenario;
import br.remoto.core.scenario.ScenarioValidator;
import br.remoto.storage.JsonCodec;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "validate", description = "Verifica se um ou mais cenários JSON são válidos.")
final class ValidateCommand implements Callable<Integer> {

    @Parameters(arity = "1..*", description = "arquivos JSON de cenário")
    List<Path> files;

    @Override
    public Integer call() {
        JsonCodec json = new JsonCodec();
        int invalid = 0;
        for (Path file : files) {
            List<String> errors;
            try {
                errors = ScenarioValidator.validate(json.read(file, Scenario.class));
            } catch (RuntimeException e) {
                errors = List.of("não foi possível ler: " + e.getMessage());
            }
            if (errors.isEmpty()) {
                System.out.println("ok      " + file);
            } else {
                invalid++;
                System.out.println("inválido " + file);
                errors.forEach(e -> System.out.println("         - " + e));
            }
        }
        return invalid == 0 ? 0 : 2;
    }
}
