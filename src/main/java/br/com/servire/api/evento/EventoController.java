package br.com.servire.api.evento;

import br.com.servire.api.evento.EventoDtos.CancelarRequest;
import br.com.servire.api.evento.EventoDtos.EventoDetalhe;
import br.com.servire.api.evento.EventoDtos.EventoRequest;
import br.com.servire.api.evento.EventoDtos.EventoResumo;
import br.com.servire.api.evento.EventoDtos.InscreverRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/eventos")
public class EventoController {

    private final EventoService service;

    public EventoController(EventoService service) {
        this.service = service;
    }

    @PreAuthorize("hasAuthority('PERM_EVENTO')")
    @GetMapping
    public List<EventoResumo> listar() {
        return service.listar();
    }

    @PreAuthorize("hasAuthority('PERM_EVENTO')")
    @GetMapping("/{id}")
    public EventoDetalhe detalhe(@PathVariable UUID id) {
        return service.detalhe(id);
    }

    @PreAuthorize("hasAuthority('PERM_EVENTO_CRIAR')")
    @PostMapping
    public EventoDetalhe criar(@RequestBody @Valid EventoRequest req) {
        return service.criar(req);
    }

    @PreAuthorize("hasAuthority('PERM_EVENTO_ALTERAR')")
    @PutMapping("/{id}")
    public EventoDetalhe atualizar(@PathVariable UUID id, @RequestBody @Valid EventoRequest req) {
        return service.atualizar(id, req);
    }

    @PreAuthorize("hasAuthority('PERM_EVENTO_ALTERAR')")
    @PostMapping("/{id}/publicar")
    public EventoDetalhe publicar(@PathVariable UUID id) {
        return service.publicar(id);
    }

    @PreAuthorize("hasAuthority('PERM_EVENTO_CANCELAR')")
    @PostMapping("/{id}/cancelar")
    public EventoDetalhe cancelar(@PathVariable UUID id, @RequestBody(required = false) CancelarRequest req) {
        return service.cancelar(id, req);
    }

    @PreAuthorize("hasAuthority('PERM_EVENTO_INSCREVER')")
    @PostMapping("/{id}/inscricoes")
    public EventoDetalhe inscrever(@PathVariable UUID id, @RequestBody @Valid InscreverRequest req) {
        return service.inscrever(id, req.pessoaId());
    }

    @PreAuthorize("hasAuthority('PERM_EVENTO_INSCREVER')")
    @DeleteMapping("/{id}/inscricoes/{inscricaoId}")
    public EventoDetalhe removerInscricao(@PathVariable UUID id, @PathVariable UUID inscricaoId) {
        return service.removerInscricao(id, inscricaoId);
    }

    @PreAuthorize("hasAuthority('PERM_EVENTO_ALTERAR')")
    @PostMapping(value = "/{id}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EventoDetalhe enviarFoto(@PathVariable UUID id, @RequestPart("foto") MultipartFile foto) {
        return service.enviarFoto(id, foto);
    }

    @PreAuthorize("hasAuthority('PERM_EVENTO_ALTERAR')")
    @PutMapping("/{id}/fotos/{fotoId}/capa")
    public EventoDetalhe definirCapa(@PathVariable UUID id, @PathVariable UUID fotoId) {
        return service.definirCapa(id, fotoId);
    }

    @PreAuthorize("hasAuthority('PERM_EVENTO_ALTERAR')")
    @DeleteMapping("/{id}/fotos/{fotoId}")
    public EventoDetalhe excluirFoto(@PathVariable UUID id, @PathVariable UUID fotoId) {
        return service.excluirFoto(id, fotoId);
    }
}
