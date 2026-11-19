package br.remoto.storage;

/** Valor lido junto com seu ETag (hash do arquivo), para controle de concorrência otimista. */
public record Versioned<T>(T value, String etag) {
}
