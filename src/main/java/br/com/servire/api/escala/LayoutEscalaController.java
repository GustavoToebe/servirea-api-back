package br.com.servire.api.escala;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/escalas/layouts")
public class LayoutEscalaController {
    private final LayoutEscalaService service;

    public LayoutEscalaController(LayoutEscalaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_ESCALA')")
    public List<LayoutEscalaDto> listar() {
        return service.listar().stream().map(LayoutEscalaDto::de).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ESCALA')")
    public LayoutEscalaDto buscar(@PathVariable UUID id) {
        return LayoutEscalaDto.de(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_ESCALA_ALTERAR')")
    public LayoutEscalaDto criar(@RequestBody LayoutEscalaDto dto) {
        return LayoutEscalaDto.de(service.criar(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ESCALA_ALTERAR')")
    public LayoutEscalaDto atualizar(@PathVariable UUID id, @RequestBody LayoutEscalaDto dto) {
        return LayoutEscalaDto.de(service.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_ESCALA_ALTERAR')")
    public void excluir(@PathVariable UUID id) {
        service.excluir(id);
    }
}
