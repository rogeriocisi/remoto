package br.remoto.storage.run;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.UserId;

import java.io.InputStream;
import java.io.OutputStream;
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

    /** Varre todas as sessões (recuperação após reinício, retenção). */
    List<RunManifest> findAllByStatus(Set<RunStatus> statuses);

    boolean delete(UserId owner, String runId);

    /**
     * A execução inteira num .zip: o manifesto, a configuração e os resultados em CSV (sem a
     * compressão gzip de cada arquivo). Não escreve nada se a execução não existe.
     */
    void exportZip(UserId owner, String runId, OutputStream out);

    /**
     * Traz de volta uma execução exportada por {@link #exportZip}: confere o manifesto, a
     * configuração (pelo hash gravado no manifesto) e os nomes dos resultados, e a grava na área
     * de {@code owner} com um id novo.
     *
     * @throws IllegalArgumentException se o arquivo não é uma execução exportada válida
     */
    RunManifest importZip(UserId owner, InputStream zip);
}
