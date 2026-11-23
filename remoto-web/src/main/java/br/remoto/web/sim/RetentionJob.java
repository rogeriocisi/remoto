package br.remoto.web.sim;

import br.remoto.storage.UserId;
import br.remoto.storage.area.AreaRepository;
import br.remoto.storage.run.RunManifest;
import br.remoto.storage.run.RunRepository;
import br.remoto.storage.run.RunStatus;
import br.remoto.web.config.RemotoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

/** Limpeza diária: execuções antigas não fixadas e áreas sem uso. */
@Component
class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final RunRepository runs;
    private final AreaRepository areas;
    private final RemotoProperties props;

    RetentionJob(RunRepository runs, AreaRepository areas, RemotoProperties props) {
        this.runs = runs;
        this.areas = areas;
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

        int areasRemoved = areas.deleteUnusedSince(Instant.now().minus(Duration.ofDays(props.retention().areaDays())));
        log.info("Retenção: {} execuções antigas e {} áreas sem uso removidas", runsRemoved, areasRemoved);
    }
}
