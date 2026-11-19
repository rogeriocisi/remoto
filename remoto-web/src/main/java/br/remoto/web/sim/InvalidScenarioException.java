package br.remoto.web.sim;

import java.util.List;

public class InvalidScenarioException extends RuntimeException {

    private final List<String> errors;

    public InvalidScenarioException(List<String> errors) {
        super("cenário inválido: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
