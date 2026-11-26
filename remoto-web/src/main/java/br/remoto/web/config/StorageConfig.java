package br.remoto.web.config;

import br.remoto.core.engine.Engines;
import br.remoto.core.engine.legacy.LegacyEngine;
import br.remoto.storage.DataLayout;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.StorageGuard;
import br.remoto.storage.UserLocks;
import br.remoto.storage.area.AreaRepository;
import br.remoto.storage.area.FileAreaRepository;
import br.remoto.storage.run.FileRunRepository;
import br.remoto.storage.run.RunRepository;
import br.remoto.storage.scenario.FileScenarioRepository;
import br.remoto.storage.scenario.PublicScenarioCatalog;
import br.remoto.storage.scenario.ScenarioRepository;
import br.remoto.web.sim.StorageQuota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/** Liga as implementações em arquivo do remoto-storage e o motor do remoto-core. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RemotoProperties.class)
class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    DataLayout dataLayout(RemotoProperties props) {
        DataLayout layout = new DataLayout(Path.of(props.dataDir()), Path.of(props.publicScenariosDir()));
        log.info("Dados em {} | cenários públicos em {}", layout.root(), layout.publicScenariosDir());
        return layout;
    }

    @Bean
    JsonCodec storageJsonCodec() {
        return new JsonCodec();
    }

    @Bean
    UserLocks userLocks() {
        return new UserLocks();
    }

    /**
     * O teto do disco, consultado pelos repositórios antes de a área crescer. Zero é sem limite,
     * o padrão de quem roda no próprio computador.
     */
    @Bean
    StorageQuota storageQuota(DataLayout layout, RemotoProperties props) {
        long area = props.storage().maxAreaBytes().toBytes();
        long all = props.storage().maxTotalBytes().toBytes();
        log.info("Cota de disco: por área, {} | no total, {}", StorageQuota.describe(area), StorageQuota.describe(all));
        return new StorageQuota(layout, area, all);
    }

    @Bean
    ScenarioRepository scenarioRepository(DataLayout layout, JsonCodec json, UserLocks locks, StorageGuard quota) {
        return new FileScenarioRepository(layout, json, locks, quota);
    }

    @Bean
    RunRepository runRepository(DataLayout layout, JsonCodec json, UserLocks locks, StorageGuard quota) {
        return new FileRunRepository(layout, json, locks, quota);
    }

    @Bean
    AreaRepository areaRepository(DataLayout layout, UserLocks locks) {
        return new FileAreaRepository(layout, locks);
    }

    @Bean
    PublicScenarioCatalog publicScenarioCatalog(DataLayout layout, JsonCodec json) {
        return new PublicScenarioCatalog(layout, json);
    }

    /** Motores disponíveis, escolhidos pelo campo engine de cada cenário: os modelos do ReMoto ("legacy"). */
    @Bean
    Engines engines(RemotoProperties props) {
        return new Engines(java.util.List.of(new LegacyEngine(props.simulation().effectiveThreadsPerRun())));
    }
}
