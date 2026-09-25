package br.com.servire.api.diocese;

import br.com.servire.api.diocese.dto.DioceseResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sugestões para o campo "Diocese" do cadastro da paróquia: as dioceses já
 * usadas por alguma paróquia. Só nome e UF (nada de dado de outra paróquia).
 * Criar e ligar diocese é pelo {@code PUT /tenant}.
 */
@RestController
@RequestMapping("/dioceses")
public class DioceseController {

    private final DioceseService dioceseService;

    public DioceseController(DioceseService dioceseService) {
        this.dioceseService = dioceseService;
    }

    @PreAuthorize("hasAuthority('PERM_PAROQUIA')")
    @GetMapping
    public List<DioceseResponse> listar() {
        return dioceseService.listarEmUso().stream().map(DioceseResponse::de).toList();
    }
}
