package br.remoto.storage;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Formato dos arquivos JSON, independente da configuração da camada web. Arquivos gravados por
 * versões novas continuam legíveis por versões antigas (campos desconhecidos são ignorados).
 */
public final class JsonCodec {

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public byte[] toBytes(Object value) {
        return mapper.writeValueAsBytes(value);
    }

    public <T> T fromBytes(byte[] content, Class<T> type) {
        return mapper.readValue(content, type);
    }

    public <T> T read(Path file, Class<T> type) {
        try (InputStream in = Files.newInputStream(file)) {
            return mapper.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeAtomically(Path file, Object value) {
        StorageFiles.writeAtomically(file, toBytes(value));
    }
}
