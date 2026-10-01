package br.com.servire.api.minhaconta;

import br.com.servire.api.storage.StorageService;
import br.com.servire.api.storage.StorageException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** HEAD fora da transação; gravação em transação curta, somente se a referência ainda existir. */
@Service
public class ReconciliadorArmazenamento {
    private final CotasService cotas;
    private final StorageService storage;
    public ReconciliadorArmazenamento(CotasService cotas,StorageService storage) {this.cotas=cotas;this.storage=storage;}
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Resultado conferir() {return conferir(0);}
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Resultado conferir(long inicio) {
        int conferidos=0,falhas=0;
        for (String caminho : cotas.fotosSemTamanho(inicio)) {
            try {
                long tamanho=storage.tamanho(caminho);
                if (tamanho<=0) {falhas++; continue;}
                cotas.registrarTamanhoConferido(caminho,tamanho); conferidos++;
            } catch (StorageException ex) {falhas++;}
        }
        long pendentes=cotas.consumo().itens().stream().filter(i -> i.codigo().equals("armazenamento_mb")).findFirst().orElseThrow().pendentes();
        long proximo=inicio+falhas;
        return new Resultado(conferidos,falhas,pendentes,proximo<pendentes ? proximo : 0);
    }
    public record Resultado(int conferidos,int falhas,long pendentes,long proximoInicio) { }
}
