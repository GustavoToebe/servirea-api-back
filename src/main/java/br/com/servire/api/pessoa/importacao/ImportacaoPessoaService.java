package br.com.servire.api.pessoa.importacao;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.pessoa.importacao.dto.ImportacaoPessoaDtos.*;
import br.com.servire.api.minhaconta.CotasService;
import br.com.servire.api.pessoa.*;
import br.com.servire.api.pessoa.dto.*;
import br.com.servire.api.voluntario.TipoVoluntario;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.time.*;
import java.util.*;

/** Lote pequeno e atômico; prévia não grava. Confirmação relê/valida o mesmo arquivo sob trava. */
@Service
public class ImportacaoPessoaService {
    private final PessoaService pessoas;
    private final ImportacaoPessoaRepository registros;
    private final CotasService cotas;
    private final AuditLogService audit;
    private final Validator validator;
    @PersistenceContext private EntityManager em;
    public ImportacaoPessoaService(PessoaService pessoas,ImportacaoPessoaRepository registros,CotasService cotas,AuditLogService audit,Validator validator) {
        this.pessoas=pessoas;this.registros=registros;this.cotas=cotas;this.audit=audit;this.validator=validator;
    }
    private record Arquivo(String hash,List<PessoaRequest> pedidos,List<Linha> linhas) { }

    @Transactional(readOnly=true)
    public Previa previa(MultipartFile arquivo) {
        Arquivo a=ler(arquivo);
        return new Previa(a.hash(),a.linhas().size(),a.linhas().stream().allMatch(l -> l.erro()==null),a.linhas());
    }
    @Transactional
    public Resultado confirmar(MultipartFile arquivo,UUID chave,String hash) {
        // Raiz antes de qualquer consulta mutável. Repetição continua possível mesmo com plano reduzido.
        cotas.reservar();
        Arquivo a=lerBytesEValidarHash(arquivo,hash);
        var anterior=registros.findByChave(chave);
        if(anterior.isPresent()) {
            var registro=anterior.get();
            if(!registro.getHashArquivo().equals(a.hash())) throw new ConflictException("A chave de confirmação já foi usada com outro arquivo.");
            return new Resultado(registro.getId(),registro.getQuantidade(),true);
        }
        a=validarLinhas(a);
        if(a.linhas().stream().anyMatch(l -> l.erro()!=null)) throw new BadRequestException("Corrija os erros ou possíveis duplicidades e gere uma nova prévia antes de confirmar.");
        LocalDate competencia=cotas.validarImportacao();
        for(var pedido:a.pedidos()) pessoas.criar(pedido);
        var registro=registros.saveAndFlush(new ImportacaoPessoa(chave,a.hash(),a.pedidos().size(),competencia));
        audit.registrar("IMPORTAR","IMPORTACAO_PESSOA",registro.getId(),List.of("quantidade"));
        return new Resultado(registro.getId(),registro.getQuantidade(),false);
    }
    private Arquivo ler(MultipartFile arquivo) {return validarLinhas(lerBytesEValidarHash(arquivo,null));}
    private Arquivo lerBytesEValidarHash(MultipartFile arquivo,String esperado) {
        if(arquivo==null || arquivo.isEmpty() || arquivo.getSize()>524288) throw new BadRequestException("Envie um CSV UTF-8 de até 512 KiB.");
        try {
            byte[] bytes=arquivo.getBytes();
            if(bytes.length>524288) throw new BadRequestException("CSV acima de 512 KiB.");
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if(esperado!=null && !hash.equals(esperado)) throw new ConflictException("O arquivo mudou após a prévia. Gere uma nova prévia.");
            String texto=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().replaceFirst("^\ufeff","");
            String[] linhas=texto.split("\r?\n",-1);
            char separador=linhas[0].contains(";") ? ';' : ',';
            if(!campos(linhas[0],separador).equals(List.of("nome","papel","cpf","email","telefone")))
                throw new BadRequestException("Cabeçalho esperado: nome;papel;cpf;email;telefone (vírgula também aceita).");
            var pedidos=new ArrayList<PessoaRequest>();var resultado=new ArrayList<Linha>();
            for(int i=1;i<linhas.length;i++) {
                if(linhas[i].isBlank()) continue;
                if(resultado.size()>=100) throw new BadRequestException("Máximo de 100 pessoas por lote.");
                try {
                    List<String> c=campos(linhas[i],separador);
                    if(c.size()!=5) throw new BadRequestException("A linha precisa ter cinco colunas.");
                    String nome=c.get(0);if(nome.isBlank() || nome.length()>120) throw new BadRequestException("Nome obrigatório, até 120 caracteres.");
                    String papel=c.get(1).toUpperCase(Locale.ROOT);
                    boolean voluntario=!papel.equals("RESPONSAVEL");
                    TipoVoluntario tipo=null;
                    if(voluntario) {try {tipo=TipoVoluntario.valueOf(papel);} catch(IllegalArgumentException ex) {throw new BadRequestException("Papel: RESPONSAVEL, COROINHA, ACOLITO, AMBOS ou MESC.");}}
                    String cpf=Formatos.cpf(c.get(2));String telefone=Formatos.telefone(c.get(4));
                    var pedido=new PessoaRequest(Set.of(voluntario ? PessoaPapel.VOLUNTARIO : PessoaPapel.RESPONSAVEL),nome,null,null,cpf,null,
                        c.get(3).isBlank() ? List.of() : List.of(new ContatoEmailRequest("Pessoal",c.get(3),true)),
                        telefone==null ? List.of() : List.of(new ContatoTelefoneRequest("Pessoal",telefone,true)),List.of(),List.of(),
                        null,null,null,null,null,null,null,null,null,null,null,null,
                        voluntario ? new VoluntarioPerfilRequest(tipo,true,null,null,null,null,false,List.of(),null,null) : null);
                    if(!validator.validate(pedido).isEmpty()) throw new BadRequestException("Contato ou cadastro inválido. Confira o e-mail.");
                    pedidos.add(pedido);resultado.add(new Linha(i+1,nome,null));
                } catch(ApiException ex) {resultado.add(new Linha(i+1,"",ex.getMessage()));}
            }
            if(resultado.isEmpty()) throw new BadRequestException("O CSV está sem pessoas.");
            return new Arquivo(hash,pedidos,resultado);
        } catch(java.io.IOException|NoSuchAlgorithmException ex) {throw new BadRequestException("Não foi possível ler o CSV UTF-8.");}
    }
    private Arquivo validarLinhas(Arquivo a) {
        var nomesDoArquivo=a.pedidos().stream().map(r -> r.nomeCompleto().toLowerCase(Locale.ROOT)).distinct().toList();
        var cpfsDoArquivo=a.pedidos().stream().map(PessoaRequest::cpf).filter(Objects::nonNull).distinct().toList();
        var nomesExistentes=new HashSet<String>();var cpfsExistentes=new HashSet<String>();
        if(!nomesDoArquivo.isEmpty()) {
            String jpql="select lower(p.nomeCompleto),p.cpf from Pessoa p where lower(p.nomeCompleto) in :nomes";
            if(!cpfsDoArquivo.isEmpty()) jpql+=" or p.cpf in :cpfs";
            var consulta=em.createQuery(jpql,Object[].class).setParameter("nomes",nomesDoArquivo);
            if(!cpfsDoArquivo.isEmpty()) consulta.setParameter("cpfs",cpfsDoArquivo);
            for(var existente:consulta.getResultList()) {nomesExistentes.add((String)existente[0]);if(existente[1]!=null) cpfsExistentes.add((String)existente[1]);}
        }
        var nomes=new HashSet<String>();var cpfs=new HashSet<String>();var resultado=new ArrayList<Linha>();int p=0;
        for(var linha:a.linhas()) {
            if(linha.erro()!=null) {resultado.add(linha);continue;}
            var pedido=a.pedidos().get(p++);String nome=pedido.nomeCompleto().toLowerCase(Locale.ROOT);String cpf=pedido.cpf();
            boolean repetido=!nomes.add(nome) || cpf!=null && !cpfs.add(cpf);
            boolean existente=nomesExistentes.contains(nome) || cpf!=null && cpfsExistentes.contains(cpf);
            resultado.add(new Linha(linha.linha(),linha.nome(),repetido || existente ? "Possível duplicidade por nome ou CPF. Confira manualmente." : null));
        }
        return new Arquivo(a.hash(),a.pedidos(),resultado);
    }
    /** CSV delimitado, aspas duplicadas; multiline deliberadamente recusado nesta versão. */
    static List<String> campos(String linha,char separador) {
        var campos=new ArrayList<String>();var atual=new StringBuilder();boolean aspas=false,fechou=false;
        for(int i=0;i<linha.length();i++) {
            char c=linha.charAt(i);
            if(aspas) {
                if(c=='"') {if(i+1<linha.length() && linha.charAt(i+1)=='"') {atual.append('"');i++;} else {aspas=false;fechou=true;}}
                else atual.append(c);
            } else if(c==separador) {campos.add(atual.toString().trim());atual.setLength(0);fechou=false;}
            else if(c=='"' && atual.isEmpty() && !fechou) aspas=true;
            else {if(fechou || c=='"') throw new BadRequestException("Aspas inválidas; não usar quebras de linha dentro das células.");atual.append(c);}
            if(atual.length()>200 || campos.size()>5) throw new BadRequestException("Coluna muito longa ou colunas em excesso.");
        }
        if(aspas) throw new BadRequestException("Aspas não fechadas; não usar células com múltiplas linhas.");
        campos.add(atual.toString().trim());return campos;
    }
}
