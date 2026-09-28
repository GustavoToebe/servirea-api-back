package br.com.servire.api.pessoa;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PessoaRepository extends JpaRepository<Pessoa, UUID>, JpaSpecificationExecutor<Pessoa> {

    /**
     * Só o perfil 1:1 no grafo. As quatro coleções são {@code List} (bags):
     * pôr mais de uma no {@code @EntityGraph} não carregava as relações de
     * verdade (o {@code GET /pessoas/{id}} de um voluntário com
     * responsável estourava {@code LazyInitializationException} no
     * controller). Quem precisa da ficha inteira usa
     * {@code PessoaService.buscarPorId}, que inicializa as coleções dentro
     * da transação.
     */
    @Override
    @EntityGraph(attributePaths = {"voluntario"})
    Optional<Pessoa> findById(UUID id);

    /**
     * Pessoas cujo e-mail principal é {@code email}. Pode haver mais de
     * uma: o e-mail principal não é único no tenant (criança cadastrada
     * com o e-mail da mãe é o caso comum, e o backfill da V030 herdou
     * isso). Quem já é responsável vem primeiro; empate pelo cadastro
     * mais antigo.
     */
    @Query("""
            SELECT p FROM Pessoa p
            WHERE EXISTS (
                SELECT 1 FROM PessoaEmail e
                WHERE e.pessoa = p
                  AND e.principal = true
                  AND lower(e.email) = lower(:email))
            ORDER BY p.eResponsavel DESC, p.createdAt ASC
            """)
    List<Pessoa> findPorEmailPrincipal(@Param("email") String email);

    @Query("SELECT new br.com.servire.api.pessoa.dto.PessoaBasicoParaDuplicidade(p.id, p.sequencial, p.nomeCompleto, p.cpf, p.dataNascimento) FROM Pessoa p")
    List<br.com.servire.api.pessoa.dto.PessoaBasicoParaDuplicidade> findAllBasico();

    @Query("SELECT new br.com.servire.api.pessoa.dto.TelefoneParaDuplicidade(t.pessoa.id, t.numero) FROM PessoaTelefone t")
    List<br.com.servire.api.pessoa.dto.TelefoneParaDuplicidade> findAllTelefones();

    @Query("SELECT new br.com.servire.api.pessoa.dto.ResponsavelParaDuplicidade(r.voluntario.id, r.responsavel.nomeCompleto) FROM PessoaRelacao r")
    List<br.com.servire.api.pessoa.dto.ResponsavelParaDuplicidade> findAllResponsaveis();

    Optional<Pessoa> findByCpf(String cpf);
}
