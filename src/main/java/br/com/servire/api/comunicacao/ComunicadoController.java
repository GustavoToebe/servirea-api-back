package br.com.servire.api.comunicacao;

import br.com.servire.api.comunicacao.dto.ComunicadoDtos.CriarRequest;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.Criado;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.DestinatarioPrevia;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.DestinatariosRequest;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.Detalhe;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.PreVisualizacao;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.PreVisualizarRequest;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.Resumo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Comunicados (PLANO-005): montar destinatários, pré-visualizar, enviar para a fila e histórico. */
@RestController
@RequestMapping("/comunicados")
public class ComunicadoController {

    private final ComunicadoService service;

    public ComunicadoController(ComunicadoService service) {
        this.service = service;
    }

    @PreAuthorize("hasAuthority('PERM_COMUNICADO_ENVIAR')")
    @PostMapping("/destinatarios")
    public List<DestinatarioPrevia> destinatarios(@RequestBody @Valid DestinatariosRequest request) {
        return service.destinatarios(request);
    }

    @PreAuthorize("hasAuthority('PERM_COMUNICADO_ENVIAR')")
    @PostMapping("/pre-visualizar")
    public PreVisualizacao preVisualizar(@RequestBody @Valid PreVisualizarRequest request) {
        return service.preVisualizar(request);
    }

    @PreAuthorize("hasAuthority('PERM_COMUNICADO_ENVIAR')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Criado criar(@RequestPart("dados") @Valid CriarRequest dados,
                        @RequestPart(value = "anexos", required = false) List<MultipartFile> anexos) {
        return service.criar(dados, anexos);
    }

    @PreAuthorize("hasAuthority('PERM_COMUNICADO')")
    @GetMapping
    public List<Resumo> listar(@RequestParam(required = false) TipoEnvio canal,
                               @RequestParam(required = false) StatusComunicado status) {
        return service.listar(canal, status);
    }

    @PreAuthorize("hasAuthority('PERM_COMUNICADO')")
    @GetMapping("/{id}")
    public Detalhe detalhe(@PathVariable UUID id) {
        return service.detalhe(id);
    }

    @PreAuthorize("hasAuthority('PERM_COMUNICADO_ENVIAR')")
    @PostMapping("/{id}/reenviar-falhas")
    public Resumo reenviarFalhas(@PathVariable UUID id) {
        return service.reenviarFalhas(id);
    }
}
