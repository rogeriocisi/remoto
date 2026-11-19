package br.remoto.storage.account;

import br.remoto.storage.UserId;

import java.io.OutputStream;
import java.util.function.Supplier;

/** Perfil e conta do usuário, incluindo exportação e exclusão de todos os dados (LGPD). */
public interface ProfileRepository {

    Profile getOrCreate(UserId user, Supplier<Profile> initial);

    Profile save(UserId user, Profile profile);

    /** Todos os arquivos do usuário num .zip. */
    void exportZip(UserId user, OutputStream out);

    /** Apaga a pasta inteira do usuário (cenários, execuções, perfil). */
    void deleteAccount(UserId user);

    long diskUsageBytes(UserId user);
}
