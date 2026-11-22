package br.remoto.storage.run;

import br.remoto.storage.DataLayout;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.UserId;
import br.remoto.storage.UserLocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;

import static br.remoto.storage.StorageTestSupport.scenario;
import static br.remoto.storage.StorageTestSupport.user;
import static org.assertj.core.api.Assertions.assertThat;

class FileRunRepositoryTest {

    @TempDir
    Path data;

    private FileRunRepository runs;
    private final UserId ana = user("ana");
    private final UserId outraSessao = UserId.newSession();

    @BeforeEach
    void setUp() {
        runs = new FileRunRepository(new DataLayout(data, data.resolve("public")), new JsonCodec(), new UserLocks());
    }

    @Test
    void criaExecucaoComCopiaDaConfiguracao() {
        RunManifest m = runs.create(ana, scenario("Reflexo H"), "motor-1");

        assertThat(m.status()).isEqualTo(RunStatus.QUEUED);
        assertThat(m.seed()).isEqualTo(42);
        assertThat(runs.find(ana, m.runId())).contains(m);
        assertThat(runs.config(ana, m.runId())).hasValueSatisfying(c -> assertThat(c.name()).isEqualTo("Reflexo H"));
        assertThat(runs.find(user("bruno"), m.runId())).isEmpty();
    }

    @Test
    void gravaELeResultadosCsvGz() throws Exception {
        RunManifest m = runs.create(ana, scenario("x"), "motor-1");
        Path dir = runs.resultsDir(ana, m.runId());

        new CsvGzResultSink(dir).series("forca-SOL", "N", new double[]{0, 1, 2}, new double[]{0.5, 1.5, 2.5});

        assertThat(runs.resultNames(ana, m.runId())).containsExactly("forca-SOL");
        try (InputStream in = ResultFiles.openCsv(dir, "forca-SOL")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("t_ms,valor[N]\n0.0,0.5\n1.0,1.5\n2.0,2.5\n");
        }
        assertThat(ResultFiles.read(dir, "forca-SOL", 2).x()).containsExactly(0.0, 2.0);
    }

    @Test
    void encontraExecucoesInterrompidasDeTodasAsSessoes() {
        RunManifest daAna = runs.create(ana, scenario("a"), "motor-1");
        RunManifest daOutra = runs.create(outraSessao, scenario("b"), "motor-1");
        runs.save(outraSessao, daOutra.running(Instant.now()));
        RunManifest terminada = runs.create(ana, scenario("c"), "motor-1");
        runs.save(ana, terminada.running(Instant.now()).finished(RunStatus.DONE, Instant.now(), null));

        assertThat(runs.findAllByStatus(EnumSet.of(RunStatus.QUEUED, RunStatus.RUNNING)))
                .extracting(RunManifest::runId)
                .containsExactlyInAnyOrder(daAna.runId(), daOutra.runId());
    }

    @Test
    void naoRessuscitaExecucaoApagada() {
        RunManifest m = runs.create(ana, scenario("x"), "motor-1");
        assertThat(runs.delete(ana, m.runId())).isTrue();

        runs.save(ana, m.running(Instant.now()));

        assertThat(runs.find(ana, m.runId())).isEmpty();
    }

    /** No Windows, regravar o manifesto enquanto outra thread o lê falhava com AccessDenied. */
    @Test
    void regravaOManifestoEnquantoOutraThreadLe() throws Exception {
        RunManifest m = runs.create(ana, scenario("x"), "motor-1");
        java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<Throwable> readerError = new java.util.concurrent.atomic.AtomicReference<>();
        Thread reader = Thread.ofPlatform().start(() -> {
            while (!stop.get()) {
                try {
                    runs.find(ana, m.runId());
                } catch (RuntimeException e) {
                    readerError.set(e);
                }
            }
        });
        try {
            for (int i = 0; i < 300; i++) {
                runs.save(ana, m.withPinned(i % 2 == 0));
            }
        } finally {
            stop.set(true);
            reader.join();
        }

        assertThat(readerError.get()).isNull();
        assertThat(runs.find(ana, m.runId())).hasValueSatisfying(r -> assertThat(r.pinned()).isFalse());
    }
}
