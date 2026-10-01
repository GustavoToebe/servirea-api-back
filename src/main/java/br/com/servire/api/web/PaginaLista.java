package br.com.servire.api.web;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
/** Contrato estável de lista: limites explícitos e ordenação com desempate por id. */
public record PaginaLista<T>(List<T> itens,int pagina,int tamanho,long total,int paginas) {
    public static <T> PaginaLista<T> de(Page<T> page) {
        return new PaginaLista<>(page.getContent(),page.getNumber(),page.getSize(),page.getTotalElements(),page.getTotalPages());
    }
    public static PageRequest pedido(int pagina,int tamanho,Sort ordenacao) {
        if (pagina<0 || pagina>100000 || tamanho<1 || tamanho>100) throw new BadRequestException("Página inválida. Use tamanho entre 1 e 100.");
        return PageRequest.of(pagina,tamanho,ordenacao);
    }
}
