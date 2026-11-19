package br.remoto.storage.run;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.UserId;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Execuções de simulação de um usuário. */
public interface RunRepository {

    /** Cria a execução (status QUEUED) com uma cópia congelada da configuração. */
    RunManifest create(UserId owner, Scenario scenario, String engineVersion);

    /** Atualiza o manifesto; não faz nada se a execução foi apagada nesse meio-tempo. */
    void save(UserId owner, RunManifest manifest);

    Optional<RunManifest> find(UserId owner, String runId);

    /** Mais recentes primeiro. */
    List<RunManifest> list(UserId owner);

    Optional<Scenario> config(UserId owner, String runId);

    Path resultsDir(UserId owner, String runId);

    List<String> resultNames(UserId owner, String runId);

    /** Varre todos os usuários e convidados (recuperação após reinício, retenção). */
    List<RunManifest> findAllByStatus(Set<RunStatus> statuses);

    boolean delete(UserId owner, String runId);
}
