package br.com.servire.api.acesso;

import br.com.servire.api.acesso.dto.MeRequest;
import br.com.servire.api.acesso.dto.MeResponse;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.integracao.SuporteSessao;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.web.ForbiddenException;
import br.com.servire.api.web.Formatos;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/me")
public class MeController {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioTenantRepository usuarioTenantRepository;
    private final PasswordEncoder passwordEncoder;

    public MeController(UsuarioRepository usuarioRepository,
                        UsuarioTenantRepository usuarioTenantRepository,
                        PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioTenantRepository = usuarioTenantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public MeResponse buscar(@AuthenticationPrincipal AuthenticatedUser atual) {
        return resposta(atual);
    }

    @PutMapping
    public MeResponse atualizar(@AuthenticationPrincipal AuthenticatedUser atual,
                                @RequestBody @Valid MeRequest request) {
        if (atual.suporte()) {
            throw new ForbiddenException("O acesso de suporte não altera o perfil de ninguém.");
        }
        Usuario usuario = usuarioRepository.findById(atual.usuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        usuario.setNome(request.nome().trim());
        usuario.setTelefone(Formatos.telefone(request.telefone()));
        usuario.setTipoTelefone(request.tipoTelefone());
        if (request.senha() != null && !request.senha().isBlank()) {
            usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
        }
        usuarioRepository.save(usuario);
        return resposta(atual);
    }

    /**
     * Sessão de suporte da Central: o "usuário" do token é o id do código de uso único, que não existe em
     * {@code usuario}. A identidade é a do operador (nome e e-mail vão para a auditoria) e o menu recebe o conjunto
     * do acesso total, o mesmo que o {@code JwtAuthenticationFilter} já concede nas requisições.
     */
    private MeResponse respostaDeSuporte(AuthenticatedUser atual) {
        SuporteSessao.Dados operador = SuporteSessao.atual();
        String nome = operador == null || operador.nome() == null ? "Suporte" : operador.nome();
        String email = operador == null || operador.email() == null ? "" : operador.email();
        return new MeResponse(atual.usuarioId(), nome, email, null, null, "Suporte",
                PermissoesDaSessao.codigosEfetivos(PermissoesDaSessao.acessoTotal()));
    }

    private MeResponse resposta(AuthenticatedUser atual) {
        if (atual.suporte()) {
            return respostaDeSuporte(atual);
        }
        Usuario usuario = usuarioRepository.findById(atual.usuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        var vinculo = usuarioTenantRepository
                .findComPerfilByUsuario_IdAndTenant_Id(atual.usuarioId(), atual.tenantId());
        String perfil = vinculo
                .map(item -> item.getPerfil() == null ? item.getRole().name() : item.getPerfil().getNome())
                .orElse("");
        List<String> permissoes = vinculo
                .map(item -> PermissoesDaSessao.codigosEfetivos(PermissoesDaSessao.de(item)))
                .orElse(List.of());
        return new MeResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                usuario.getTipoTelefone(), usuario.getTelefone(), perfil, permissoes);
    }
}
