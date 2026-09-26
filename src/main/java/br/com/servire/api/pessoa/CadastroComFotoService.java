package br.com.servire.api.pessoa;

import br.com.servire.api.pessoa.dto.PessoaRequest;
import br.com.servire.api.voluntario.VoluntarioService;
import br.com.servire.api.web.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Ficha e foto numa transação só (teste de telas de 26/09/2026). Antes o
 * front salvava a pessoa e depois mandava a foto noutra chamada: com o
 * Storage fora do ar a pessoa ficava gravada, a tela seguia em "Nova pessoa"
 * e cada novo "Salvar" criava mais uma. Agora, se a foto falha, a ficha
 * também não é gravada.
 *
 * <p>O envio ao Storage acontece antes do commit; se o commit falhar depois,
 * sobra um arquivo sem dono no bucket (raro e inofensivo: o caminho só é
 * lido a partir de {@code voluntario.foto_path}).
 */
@Service
public class CadastroComFotoService {

    private final PessoaService pessoaService;
    private final VoluntarioService voluntarioService;

    public CadastroComFotoService(PessoaService pessoaService, VoluntarioService voluntarioService) {
        this.pessoaService = pessoaService;
        this.voluntarioService = voluntarioService;
    }

    @Transactional
    public Pessoa criar(PessoaRequest request, MultipartFile foto) {
        Pessoa pessoa = pessoaService.criar(request);
        return comFoto(pessoa.getId(), foto);
    }

    @Transactional
    public Pessoa atualizar(UUID id, PessoaRequest request, MultipartFile foto) {
        pessoaService.atualizar(id, request);
        return comFoto(id, foto);
    }

    private Pessoa comFoto(UUID id, MultipartFile foto) {
        if (foto != null && !foto.isEmpty()) {
            if (pessoaService.buscarPorId(id).getVoluntario() == null) {
                throw new BadRequestException("Foto só pode ser enviada para quem tem o papel de voluntário.");
            }
            voluntarioService.definirFoto(id, foto);
        }
        return pessoaService.buscarPorId(id);
    }
}
