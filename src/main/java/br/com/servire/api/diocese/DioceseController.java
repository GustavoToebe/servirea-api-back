package br.com.servire.api.diocese;

import br.com.servire.api.diocese.dto.DioceseRequest;
import br.com.servire.api.diocese.dto.DioceseResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Dioceses e cota de servidores. Só o operador ({@code PERM_BACKOFFICE}). */
@RestController
@RequestMapping("/admin/dioceses")
@PreAuthorize("hasAuthority('PERM_BACKOFFICE')")
public class DioceseController {

    private final DioceseService dioceseService;

    public DioceseController(DioceseService dioceseService) {
        this.dioceseService = dioceseService;
    }

    @GetMapping
    public List<DioceseResponse> listar() {
        return dioceseService.listar();
    }

    @PostMapping
    public DioceseResponse criar(@RequestBody @Valid DioceseRequest request) {
        return dioceseService.criar(request);
    }

    @PutMapping("/{id}")
    public DioceseResponse atualizar(@PathVariable UUID id, @RequestBody @Valid DioceseRequest request) {
        return dioceseService.atualizar(id, request);
    }
}
