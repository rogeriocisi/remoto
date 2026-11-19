package br.remoto.legacy;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converte entre o campo {@code legacy} do cenário (mapa genérico) e {@link LegacyConfig}. */
public final class LegacyJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private LegacyJson() {
    }

    public static LegacyConfig toConfig(Map<String, Object> legacy) {
        if (legacy == null) {
            throw new IllegalArgumentException("cenário sem o campo legacy");
        }
        LegacyConfig config = MAPPER.convertValue(legacy, LegacyConfig.class);
        if (!LegacyConfig.FORMAT.equals(config.format())) {
            throw new IllegalArgumentException("formato legacy não suportado: " + config.format());
        }
        return config;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(LegacyConfig config) {
        return MAPPER.convertValue(config, LinkedHashMap.class);
    }
}
