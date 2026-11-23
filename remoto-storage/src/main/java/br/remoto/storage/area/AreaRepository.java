package br.remoto.storage.area;

import br.remoto.storage.UserId;

import java.io.OutputStream;
import java.time.Instant;

/** A área de um navegador como um todo: exportar tudo, apagar tudo, medir o espaço e o uso. */
public interface AreaRepository {

    /**
     * Registra que a área foi usada agora (no máximo uma vez por hora; só se ela já existe). A
     * limpeza conta o tempo sem uso a partir daí, porque só ler não muda a data dos arquivos.
     */
    void touch(UserId user);

    /** Apaga as áreas sem uso desde {@code limit}; devolve quantas apagou. */
    int deleteUnusedSince(Instant limit);

    /** Todos os arquivos da área num .zip. */
    void exportZip(UserId user, OutputStream out);

    /** Apaga a pasta inteira da área (cenários e execuções). */
    void delete(UserId user);

    long diskUsageBytes(UserId user);
}
