package br.remoto.storage;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdsTest {

    @Test
    void ulidTem26CaracteresEOrdenaPorTempo() {
        String antes = Ids.ulid(1_700_000_000_000L, new SplittableRandom(1));
        String depois = Ids.ulid(1_700_000_000_001L, new SplittableRandom(1));

        assertThat(antes).hasSize(26).matches("[0-9A-HJKMNP-TV-Z]{26}");
        assertThat(antes).isLessThan(depois);
    }

    @Test
    void rejeitaIdsQueEscapamDoDiretorio() {
        assertThat(Ids.isSafe("01JAZ0000000000000000000")).isTrue();
        assertThat(Ids.isSafe("remoto-17-default-soleus-contraction")).isTrue();
        for (String evil : new String[]{"../x", "a/b", "a\\b", "..", ".hidden", "", "a.json"}) {
            assertThat(Ids.isSafe(evil)).as(evil).isFalse();
        }
        assertThatThrownBy(() -> Ids.requireSafe("../../etc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userIdNaoAceitaValoresArbitrarios() {
        assertThat(UserId.newSession()).isNotEqualTo(UserId.newSession());
        assertThat(new UserId(UserId.newSession().value())).isNotNull();
        assertThatThrownBy(() -> new UserId("u-0123456789abcdef0123456789abcdef"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserId("../admin")).isInstanceOf(IllegalArgumentException.class);
    }
}
