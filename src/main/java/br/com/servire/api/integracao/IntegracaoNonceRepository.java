package br.com.servire.api.integracao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface IntegracaoNonceRepository extends JpaRepository<IntegracaoNonce, IntegracaoNonce.Chave> {

    @Modifying
    @Query("delete from IntegracaoNonce n where n.recebidoEm < :limite")
    int apagarAnterioresA(Instant limite);
}
