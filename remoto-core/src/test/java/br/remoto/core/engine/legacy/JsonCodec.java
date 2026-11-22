package br.remoto.core.engine.legacy;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * O JSON dos cenários como o site e a CLI o gravam e leem (a mesma configuração do
 * {@code br.remoto.storage.JsonCodec}, que não pode ser usado aqui porque o remoto-storage depende
 * do remoto-core).
 */
final class JsonCodec {

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    byte[] toBytes(Object value) {
        return mapper.writeValueAsBytes(value);
    }

    <T> T fromBytes(byte[] content, Class<T> type) {
        return mapper.readValue(content, type);
    }
}
