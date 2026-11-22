package br.remoto.storage.area;

import br.remoto.storage.UserId;

import java.io.OutputStream;

/** A área de uma sessão como um todo: exportar tudo, apagar tudo e medir o espaço. */
public interface AreaRepository {

    /** Todos os arquivos da área num .zip. */
    void exportZip(UserId user, OutputStream out);

    /** Apaga a pasta inteira da área (cenários e execuções). */
    void delete(UserId user);

    long diskUsageBytes(UserId user);
}
