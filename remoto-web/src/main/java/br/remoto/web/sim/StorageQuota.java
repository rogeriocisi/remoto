package br.remoto.web.sim;

import br.remoto.storage.DataLayout;
import br.remoto.storage.StorageFiles;
import br.remoto.storage.StorageGuard;
import br.remoto.storage.UserId;
import br.remoto.web.i18n.Messages;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Quanto o disco pode crescer: um teto por área e um teto para todas elas somadas.
 *
 * <p>Não há login, e um cookie novo é uma área nova: o teto por área segura o uso exagerado de
 * quem está de boa-fé, e o teto total é o que protege a máquina — e o que mais roda nela — de
 * quem não está. Os dois são consultados antes das escritas que crescem (criar uma execução,
 * importar, gravar um cenário), pelo {@link StorageGuard} que o armazenamento recebe.
 *
 * <p>Zero em qualquer um dos dois quer dizer "sem limite", que é o padrão: num computador
 * pessoal não faz sentido limitar, e em servidor público os valores vêm da configuração
 * ({@code remoto.storage.*}, REMOTO_STORAGE_*).
 *
 * <p>O tamanho de uma área sai de uma varredura da pasta dela, que é pequena; o total, de uma
 * varredura de todas, guardada por {@link #TOTAL_FOR} para não repetir a cada requisição. O
 * atraso disso é aceitável: o teto total existe para o disco não acabar, e não para ser exato no
 * último megabyte.
 */
public class StorageQuota implements StorageGuard {

    private static final Duration TOTAL_FOR = Duration.ofSeconds(30);

    private final DataLayout layout;
    private final long maxAreaBytes;
    private final long maxTotalBytes;

    private volatile long total;
    private volatile Instant measuredAt = Instant.EPOCH;

    public StorageQuota(DataLayout layout, long maxAreaBytes, long maxTotalBytes) {
        this.layout = layout;
        this.maxAreaBytes = maxAreaBytes;
        this.maxTotalBytes = maxTotalBytes;
    }

    @Override
    public void requireRoom(UserId user) {
        if (maxAreaBytes > 0) {
            long area = StorageFiles.sizeOf(layout.userDir(user));
            if (area >= maxAreaBytes) {
                throw new QuotaExceededException(Messages.msg("quota.areaBytes", size(maxAreaBytes)));
            }
        }
        if (maxTotalBytes > 0 && totalBytes() >= maxTotalBytes) {
            throw new QuotaExceededException(Messages.msg("quota.diskFull"));
        }
    }

    /** O que todas as áreas ocupam, medido no máximo a cada {@link #TOTAL_FOR}. */
    public long totalBytes() {
        Instant now = Instant.now();
        if (measuredAt.isBefore(now.minus(TOTAL_FOR))) {
            Path sessions = layout.sessionsDir();
            total = StorageFiles.sizeOf(sessions);
            measuredAt = now;
        }
        return total;
    }

    /** Para a linha do log na subida: "sem limite" quando o teto é zero. */
    public static String describe(long bytes) {
        return bytes > 0 ? size(bytes) : "sem limite";
    }

    /** O teto como ele foi escrito na configuração, na maior unidade que couber inteira. */
    static String size(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return bytes / (1024L * 1024 * 1024) + " GB";
        }
        if (bytes >= 1024 * 1024) {
            return bytes / (1024 * 1024) + " MB";
        }
        return Math.max(1, bytes / 1024) + " KB";
    }
}
