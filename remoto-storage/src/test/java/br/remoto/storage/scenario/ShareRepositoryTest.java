package br.remoto.storage.scenario;

import br.remoto.storage.DataLayout;
import br.remoto.storage.JsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static br.remoto.storage.StorageTestSupport.scenario;
import static org.assertj.core.api.Assertions.assertThat;

class ShareRepositoryTest {

    @TempDir
    Path data;

    @Test
    void mesmoConteudoGeraOMesmoLinkIndependenteDoId() {
        ShareRepository shares = new ShareRepository(new DataLayout(data, data.resolve("public")), new JsonCodec());

        String a = shares.share(scenario("Reflexo H").withId("AAA"));
        String b = shares.share(scenario("Reflexo H").withId("BBB"));
        String c = shares.share(scenario("Outro"));

        assertThat(a).isEqualTo(b).hasSize(64);
        assertThat(c).isNotEqualTo(a);
        assertThat(shares.find(a)).hasValueSatisfying(s -> assertThat(s.name()).isEqualTo("Reflexo H"));
        assertThat(shares.find("../x")).isEmpty();
    }
}
