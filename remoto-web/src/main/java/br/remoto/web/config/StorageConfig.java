package br.remoto.web.config;

import br.remoto.core.engine.Engines;
import br.remoto.core.engine.placeholder.PlaceholderPoolEngine;
import br.remoto.core.engine.legacy.LegacyEngine;
import br.remoto.storage.DataLayout;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.UserLocks;
import br.remoto.storage.account.FileProfileRepository;
import br.remoto.storage.account.ProfileRepository;
import br.remoto.storage.run.FileRunRepository;
import br.remoto.storage.run.RunRepository;
import br.remoto.storage.scenario.FileScenarioRepository;
import br.remoto.storage.scenario.PublicScenarioCatalog;
import br.remoto.storage.scenario.ScenarioRepository;
import br.remoto.storage.scenario.ShareRepository;
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

    @Bean
    ScenarioRepository scenarioRepository(DataLayout layout, JsonCodec json, UserLocks locks) {
        return new FileScenarioRepository(layout, json, locks);
    }

    @Bean
    RunRepository runRepository(DataLayout layout, JsonCodec json, UserLocks locks) {
        return new FileRunRepository(layout, json, locks);
    }

    @Bean
    ProfileRepository profileRepository(DataLayout layout, JsonCodec json, UserLocks locks) {
        return new FileProfileRepository(layout, json, locks);
    }

    @Bean
    ShareRepository shareRepository(DataLayout layout, JsonCodec json) {
        return new ShareRepository(layout, json);
    }

    @Bean
    PublicScenarioCatalog publicScenarioCatalog(DataLayout layout, JsonCodec json) {
        return new PublicScenarioCatalog(layout, json);
    }

    /**
     * Motores disponíveis: os modelos originais do ReMoto ("legacy") e o motor provisório
     * ("placeholder"), escolhidos pelo campo engine de cada cenário.
     */
    @Bean
    Engines engines(RemotoProperties props) {
        return new Engines(java.util.List.of(
                new LegacyEngine(props.simulation().effectiveThreadsPerRun()), new PlaceholderPoolEngine()));
    }
}
