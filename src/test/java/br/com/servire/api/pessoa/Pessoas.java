package br.com.servire.api.pessoa;

import br.com.servire.api.voluntario.Voluntario;

/** Fábrica de teste: pessoa VOLUNTARIO + perfil 1:1 (MapsId). */
public final class Pessoas {

    private Pessoas() {
    }

    public static Pessoa voluntario(String nome) {
        Pessoa pessoa = new Pessoa(PessoaPapel.VOLUNTARIO, nome);
        Voluntario perfil = new Voluntario();
        pessoa.setVoluntario(perfil);
        return pessoa;
    }

    public static Voluntario persistirVoluntario(PessoaRepository repositorio, String nome) {
        return repositorio.saveAndFlush(voluntario(nome)).getVoluntario();
    }
}
