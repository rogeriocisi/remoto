package br.remoto.storage.scenario;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.DataLayout;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.StaleWriteException;
import br.remoto.storage.UserId;
import br.remoto.storage.UserLocks;
import br.remoto.storage.Versioned;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static br.remoto.storage.StorageTestSupport.scenario;
import static br.remoto.storage.StorageTestSupport.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileScenarioRepositoryTest {

    @TempDir
    Path data;

    private FileScenarioRepository repo;
    private final UserId ana = user("ana");
    private final UserId bruno = user("bruno");

    @BeforeEach
    void setUp() {
        repo = new FileScenarioRepository(new DataLayout(data, data.resolve("public")), new JsonCodec(), new UserLocks());
    }

    @Test
    void criaLeListaEApaga() {
        Versioned<Scenario> created = repo.create(ana, scenario("Reflexo H"));

        assertThat(created.value().id()).isNotBlank();
        assertThat(repo.find(ana, created.value().id())).contains(created);
        assertThat(repo.list(ana)).extracting(Scenario::name).containsExactly("Reflexo H");

        assertThat(repo.delete(ana, created.value().id())).isTrue();
        assertThat(repo.list(ana)).isEmpty();
    }

    @Test
    void usuariosNaoEnxergamOsCenariosUnsDosOutros() {
        Versioned<Scenario> daAna = repo.create(ana, scenario("da Ana"));

        assertThat(repo.find(bruno, daAna.value().id())).isEmpty();
        assertThat(repo.list(bruno)).isEmpty();
        assertThat(repo.delete(bruno, daAna.value().id())).isFalse();
        assertThat(repo.find(ana, daAna.value().id())).isPresent();
    }

    @Test
    void gravacaoComEtagDesatualizadoEhRecusada() {
        Versioned<Scenario> v1 = repo.create(ana, scenario("v1"));
        Scenario renamed = new Scenario(v1.value().id(), "v2", "", 1, 42, 100, 0.05, v1.value().nuclei());

        Versioned<Scenario> v2 = repo.update(ana, renamed, v1.etag());

        assertThat(v2.etag()).isNotEqualTo(v1.etag());
        assertThatThrownBy(() -> repo.update(ana, renamed, v1.etag())).isInstanceOf(StaleWriteException.class);
    }

    @Test
    void idsMaliciososNaoSaemDaPastaDoUsuario() {
        assertThat(repo.find(ana, "../../shares/x")).isEmpty();
        assertThat(repo.delete(ana, "..")).isFalse();
    }

    @Test
    void muitasGravacoesConcorrentesNaoCorrompemArquivos() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Versioned<Scenario>>> futures = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                UserId owner = i % 2 == 0 ? ana : bruno;
                String name = "cenario-" + i;
                futures.add(pool.submit(() -> repo.create(owner, scenario(name))));
            }
            for (Future<Versioned<Scenario>> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }

        assertThat(repo.list(ana)).hasSize(20);
        assertThat(repo.list(bruno)).hasSize(20);
    }
}
