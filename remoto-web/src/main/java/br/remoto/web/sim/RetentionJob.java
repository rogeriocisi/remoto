package br.remoto.web.sim;

import br.remoto.storage.DataLayout;
import br.remoto.storage.StorageFiles;
import br.remoto.storage.UserId;
import br.remoto.storage.run.RunManifest;
import br.remoto.storage.run.RunRepository;
import br.remoto.storage.run.RunStatus;
import br.remoto.web.config.RemotoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

/** Limpeza diária: execuções antigas não fixadas e áreas de sessão abandonadas. */
@Component
class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final RunRepository runs;
    private final DataLayout layout;
    private final RemotoProperties props;

    RetentionJob(RunRepository runs, DataLayout layout, RemotoProperties props) {
        this.runs = runs;
        this.layout = layout;
        this.props = props;
    }

    @Scheduled(cron = "${remoto.retention.cron:0 30 3 * * *}")
    void cleanup() {
        Instant runLimit = Instant.now().minus(Duration.ofDays(props.retention().runDays()));
        int runsRemoved = 0;
        for (RunManifest m : runs.findAllByStatus(EnumSet.of(RunStatus.DONE, RunStatus.FAILED, RunStatus.CANCELED))) {
            if (!m.pinned() && m.finishedAt() != null && m.finishedAt().isBefore(runLimit)
                    && runs.delete(new UserId(m.ownerId()), m.runId())) {
                runsRemoved++;
            }
        }

        Instant areaLimit = Instant.now().minus(Duration.ofHours(props.retention().sessionHours()));
        int areasRemoved = 0;
        for (Path dir : StorageFiles.subdirectories(layout.sessionsDir())) {
            if (lastModified(dir).isBefore(areaLimit)) {
                StorageFiles.deleteRecursively(dir);
                areasRemoved++;
            }
        }
        log.info("Retenção: {} execuções antigas e {} áreas de sessão abandonadas removidas", runsRemoved, areasRemoved);
    }

    private static Instant lastModified(Path dir) {
        try {
            return Files.getLastModifiedTime(dir).toInstant();
        } catch (IOException e) {
            return Instant.now();
        }
    }
}
