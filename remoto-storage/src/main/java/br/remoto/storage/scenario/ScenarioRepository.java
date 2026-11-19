package br.remoto.storage.scenario;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.UserId;
import br.remoto.storage.Versioned;

import java.util.List;
import java.util.Optional;

/**
 * Cenários de um usuário. Interface para que a implementação em arquivos possa ser trocada por
 * outra (banco, S3...) sem mexer na camada web.
 */
public interface ScenarioRepository {

    List<Scenario> list(UserId owner);

    Optional<Versioned<Scenario>> find(UserId owner, String id);

    /** Grava um cenário novo com id gerado pelo servidor. */
    Versioned<Scenario> create(UserId owner, Scenario scenario);

    /**
     * Substitui um cenário existente.
     *
     * @param expectedEtag ETag lido antes da edição; {@code null} desativa a verificação
     * @throws br.remoto.storage.StaleWriteException se o arquivo mudou desde a leitura
     * @throws java.util.NoSuchElementException se o cenário não existe
     */
    Versioned<Scenario> update(UserId owner, Scenario scenario, String expectedEtag);

    boolean delete(UserId owner, String id);
}
