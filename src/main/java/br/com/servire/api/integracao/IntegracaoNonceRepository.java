package br.com.servire.api.integracao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface IntegracaoNonceRepository extends JpaRepository<IntegracaoNonce, IntegracaoNonce.Chave> {

    /**
     * Insere o nonce; 0 = já existia (repetição). {@code ON CONFLICT} em
     * vez de {@code save}: com a chave preenchida à mão o {@code save} fazia
     * merge e o nonce repetido passava (25/09/2026). Tabela global, fora do
     * Native Query Gate (que vale só para tabela tenant-aware).
     */
    @Modifying
    @Query(value = "INSERT INTO public.integracao_nonce (chave_id, nonce, recebido_em) "
            + "VALUES (:chaveId, :nonce, now()) ON CONFLICT DO NOTHING", nativeQuery = true)
    int registrar(String chaveId, String nonce);

    @Modifying
    @Query("delete from IntegracaoNonce n where n.recebidoEm < :limite")
    int apagarAnterioresA(Instant limite);
}
