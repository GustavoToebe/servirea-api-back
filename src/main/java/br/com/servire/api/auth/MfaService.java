package br.com.servire.api.auth;

import br.com.servire.api.security.OpaqueTokenGenerator;
import br.com.servire.api.web.ResourceNotFoundException;
import br.com.servire.api.web.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/** Todas as verificações/alterações usam o mesmo lock do usuário que o login e o refresh. */
@Service
public class MfaService {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder senhas;
    private final MfaCifra cifra;
    private final JdbcTemplate jdbc;
    private final RefreshTokenService refresh;
    private final br.com.servire.api.audit.AuditLogService auditoria;
    private final SecureRandom random = new SecureRandom();
    public MfaService(UsuarioRepository usuarios, PasswordEncoder senhas, MfaCifra cifra,
                      JdbcTemplate jdbc, RefreshTokenService refresh, br.com.servire.api.audit.AuditLogService auditoria) {
        this.usuarios = usuarios; this.senhas = senhas; this.cifra = cifra;
        this.jdbc = jdbc; this.refresh = refresh; this.auditoria = auditoria;
    }
    @Transactional(readOnly = true)
    public Status status(UUID id) {
        Usuario op = usuarios.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        return new Status(op.getMfaSegredo() != null, cifra.configurada(), jdbc.queryForObject(
                "select count(*) from usuario_mfa_recuperacao where usuario_id = ? and usado_em is null", Integer.class, id));
    }
    @Transactional
    public Preparacao preparar(UUID id, String senha, String ip) {
        Usuario op = operador(id, senha);
        if (op.getMfaSegredo() != null) throw new MfaException(HttpStatus.CONFLICT, "MFA já está ativo.", "MFA_JA_ATIVO");
        String segredo = Totp.novoSegredo(); Instant ate = Instant.now().plusSeconds(600);
        op.prepararMfa(cifra.cifrar(id, segredo), ate);
        auditoria.registrar("MFA_PREPARADO", "USUARIO", id, List.of("mfa"));
        return new Preparacao(segredo, ate);
    }
    @Transactional
    public Recuperacao ativar(UUID id, String senha, String codigo, String ip) {
        Usuario op = operador(id, senha);
        if (op.getMfaSegredo() != null || op.getMfaPendente() == null || !Instant.now().isBefore(op.getMfaPendenteAte()))
            throw new MfaException(HttpStatus.CONFLICT, "Prepare novamente a configuração do MFA.", "MFA_PREPARACAO_EXPIRADA");
        long passo = Totp.verificar(cifra.decifrar(id, op.getMfaPendente()), codigo, Instant.now(), -1);
        if (passo < 0) throw MfaException.invalido();
        op.ativarMfa(passo);
        List<String> codigos=gerarRecuperacao(id);
        refresh.revogarNaTransacao(id);
        auditoria.registrar("MFA_ATIVADO", "USUARIO", id, List.of("mfa"));
        return new Recuperacao(List.copyOf(codigos));
    }
    @Transactional
    public void desativar(UUID id, String senha, String codigo, String ip) {
        Usuario op = operador(id, senha);
        if (op.getMfaSegredo() == null) throw new MfaException(HttpStatus.CONFLICT, "MFA não está ativo.", "MFA_INATIVO");
        verificar(op, codigo);
        op.desativarMfa(); jdbc.update("delete from usuario_mfa_recuperacao where usuario_id = ?", id);
        refresh.revogarNaTransacao(id); auditoria.registrar("MFA_DESATIVADO", "USUARIO", id, List.of("mfa"));
    }
    @Transactional
    public Recuperacao renovarRecuperacao(UUID id,String senha,String codigo,String ip) {
        Usuario op=operador(id,senha);
        if(op.getMfaSegredo()==null) throw new MfaException(HttpStatus.CONFLICT,"MFA não está ativo.","MFA_INATIVO");
        verificar(op,codigo);op.invalidarCredenciais();
        List<String> codigos=gerarRecuperacao(id);refresh.revogarNaTransacao(id);
        auditoria.registrar("MFA_RECUPERACAO_RENOVADA","USUARIO",id,List.of("mfa"));
        return new Recuperacao(List.copyOf(codigos));
    }
    private List<String> gerarRecuperacao(UUID id) {
        jdbc.update("delete from usuario_mfa_recuperacao where usuario_id = ?", id);
        List<String> codigos = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            byte[] bytes = new byte[16]; random.nextBytes(bytes);
            String bruto = HexFormat.of().formatHex(bytes); codigos.add(bruto);
            jdbc.update("insert into usuario_mfa_recuperacao(usuario_id, codigo_hash) values (?, ?)", id, OpaqueTokenGenerator.hash(bruto));
        }
        return codigos;
    }
    @Transactional
    public void alterarSenha(UUID id,String senhaAtual,String novaSenha,String codigo) {
        Usuario op=operador(id,senhaAtual);
        verificar(op,codigo);
        if(novaSenha.length()<8 || novaSenha.length()>72) throw new br.com.servire.api.web.BadRequestException("A nova senha deve ter entre 8 e 72 caracteres.");
        op.setSenhaHash(senhas.encode(novaSenha));op.invalidarCredenciais();
        refresh.revogarNaTransacao(id);
        auditoria.registrar("SENHA_ALTERADA","USUARIO",id,List.of("senha"));
    }
    /** Chamado após senha válida, dentro da transação e do lock do login. */
    public void verificar(Usuario op, String codigo) {
        if (op.getMfaSegredo() == null) return;
        if (codigo == null || codigo.isBlank()) throw new MfaException(HttpStatus.UNAUTHORIZED,
                "Informe o código do aplicativo autenticador ou um código de recuperação.", "MFA_NECESSARIO");
        codigo = codigo.trim();
        if (codigo.matches("[0-9a-f]{32}")) {
            if (jdbc.update("update usuario_mfa_recuperacao set usado_em = ? where usuario_id = ? and codigo_hash = ? and usado_em is null",
                    Instant.now().atOffset(java.time.ZoneOffset.UTC), op.getId(), OpaqueTokenGenerator.hash(codigo)) == 1) return;
        } else {
            long passo = Totp.verificar(cifra.decifrar(op.getId(), op.getMfaSegredo()), codigo, Instant.now(), op.getMfaUltimoPasso());
            if (passo >= 0) {op.consumirPassoMfa(passo); return;}
        }
        throw MfaException.invalido();
    }
    private Usuario operador(UUID id, String senha) {
        Usuario op = usuarios.buscarParaAlterar(id).orElseThrow(() -> new UnauthorizedException("Sessão inválida."));
        if (!op.isAtivo()) throw new UnauthorizedException("Sessão inválida.");
        if (!senhas.matches(senha, op.getSenhaHash())) throw new MfaException(HttpStatus.UNAUTHORIZED, "A senha atual não confere.", "MFA_SENHA_INVALIDA");
        return op;
    }
    public record Status(boolean ativo, boolean configurado, int codigosRestantes) {}
    public record Preparacao(String segredo, Instant expiraEm) {
        @Override public String toString() {return "Preparacao[segredo=***, expiraEm=" + expiraEm + "]";}
    }
    public record Recuperacao(List<String> codigos) {
        @Override public String toString() {return "Recuperacao[codigos=***]";}
    }
}
