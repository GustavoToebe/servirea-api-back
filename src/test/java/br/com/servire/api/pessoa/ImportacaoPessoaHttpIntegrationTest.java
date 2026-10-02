package br.com.servire.api.pessoa;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.pessoa.importacao.*;
import br.com.servire.api.pessoa.importacao.dto.ImportacaoPessoaDtos.Opcoes;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import br.com.servire.api.integracao.*;
import br.com.servire.api.minhaconta.*;
import br.com.servire.api.tenant.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ImportacaoPessoaHttpIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc; @Autowired TenantRepository tenants; @Autowired PessoaRepository pessoas;
    @Autowired DireitosLocaisRepository direitos; @Autowired CotasService cotas;
    @Autowired ImportacaoPessoaService service; @Autowired ImportacaoPessoaRepository registros;
    UUID tenant;
    @BeforeEach void preparar() {tenant=nova();TenantContext.set(tenant);}
    @AfterEach void limpar() {TenantContext.clear();}
    UUID nova() {String x=UUID.randomUUID().toString();return tenants.saveAndFlush(new Tenant(x,x,"Importação",Tenant.Status.ATIVO)).getId();}
    MockMultipartFile csv(String corpo) {return new MockMultipartFile("arquivo","pessoas.csv","text/csv",("nome;papel;cpf;email;telefone\n"+corpo).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    void limitar(String limites) {var d=new DireitosLocais(tenant,UUID.randomUUID());d.setLimites(limites);d.setSituacao("ATIVA");d.setAcessoLiberado(true);d.setConfirmadoEm(Instant.now());direitos.saveAndFlush(d);}
    @Test void previaNaoGravaEConfirmacaoHttpEhIdempotente() throws Exception {
        var arquivo=csv("Ana;COROINHA;;ana@example.test;45999990000\nMaria;RESPONSAVEL;;;\n");
        var previa=service.previa(arquivo);assertThat(previa.podeConfirmar()).isTrue();assertThat(pessoas.count()).isZero();
        UUID chave=UUID.randomUUID();
        mvc.perform(multipart("/pessoas/importacoes/previa").file(arquivo).with(csrf()).with(user("criador").authorities(new SimpleGrantedAuthority("PERM_PESSOA"),new SimpleGrantedAuthority("PERM_PESSOA_CRIAR"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.quantidade").value(2));
        TenantContext.set(tenant);
        mvc.perform(multipart("/pessoas/importacoes/confirmar").file(arquivo).with(csrf()).param("chave",chave.toString()).param("hash",previa.hash())
            .with(user("criador").authorities(new SimpleGrantedAuthority("PERM_PESSOA"),new SimpleGrantedAuthority("PERM_PESSOA_CRIAR"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.quantidade").value(2)).andExpect(jsonPath("$.repetida").value(false));
        TenantContext.set(tenant);assertThat(service.confirmar(arquivo,chave,previa.hash()).repetida()).isTrue();
        assertThat(pessoas.count()).isEqualTo(2);assertThat(registros.count()).isEqualTo(1);
        assertThat(pessoas.findAll()).filteredOn(Pessoa::isVoluntario).singleElement().satisfies(p -> assertThat(p.getVoluntario().isAutorizaWhatsapp()).isFalse());
        assertThat(cotas.consumo().itens().stream().filter(i -> i.codigo().equals("importacoes_mes")).findFirst().orElseThrow().usado()).isEqualTo(1);
    }
    @Test void leitorNaoImportaENemLePrevia() throws Exception {
        var arquivo=csv("Ana;RESPONSAVEL;;;\n");
        mvc.perform(multipart("/pessoas/importacoes/previa").file(arquivo).with(csrf()).with(user("leitor").authorities(new SimpleGrantedAuthority("PERM_PESSOA"))))
            .andExpect(status().isForbidden());
        TenantContext.set(tenant);
        mvc.perform(multipart("/pessoas/importacoes/confirmar").file(arquivo).with(csrf()).param("chave",UUID.randomUUID().toString()).param("hash","a".repeat(64))
            .with(user("leitor").authorities(new SimpleGrantedAuthority("PERM_PESSOA"))))
            .andExpect(status().isForbidden());
        TenantContext.set(tenant);assertThat(pessoas.count()).isZero();
    }
    @Test void quotaDePessoasReverteLoteInteiroESemConsumirImportacao() {
        limitar("{\"pessoas\":1,\"importacoes_mes\":1}");var arquivo=csv("Ana;RESPONSAVEL;;;\nMaria;RESPONSAVEL;;;\n");
        var previa=service.previa(arquivo);
        assertThatThrownBy(() -> service.confirmar(arquivo,UUID.randomUUID(),previa.hash())).isInstanceOf(CotaException.class);
        assertThat(pessoas.count()).isZero();assertThat(registros.count()).isZero();
    }
    @Test void quotaMensalZeroPermitePreviaMasNaoConfirmacao() {
        limitar("{\"importacoes_mes\":0}");var arquivo=csv("Ana;RESPONSAVEL;;;\n");var previa=service.previa(arquivo);
        assertThat(previa.podeConfirmar()).isTrue();
        assertThatThrownBy(() -> service.confirmar(arquivo,UUID.randomUUID(),previa.hash())).isInstanceOf(CotaException.class);
        assertThat(pessoas.count()).isZero();assertThat(registros.count()).isZero();
    }
    @Test void duplicidadesOuArquivoAlteradoBloqueiamSemLoteParcial() {
        var arquivo=csv("Ana;RESPONSAVEL;52998224725;;\nMaria;RESPONSAVEL;52998224725;;\n");
        assertThat(service.previa(arquivo).podeConfirmar()).isFalse();
        var correto=csv("Ana;RESPONSAVEL;;;\n");var previa=service.previa(correto);UUID chave=UUID.randomUUID();
        assertThatThrownBy(() -> service.confirmar(arquivo,chave,previa.hash())).isInstanceOf(br.com.servire.api.web.ConflictException.class);
        service.confirmar(correto,chave,previa.hash());
        assertThat(service.previa(correto).podeConfirmar()).isFalse();
        var outro=csv("Maria;RESPONSAVEL;;;\n");
        assertThatThrownBy(() -> service.confirmar(outro,chave,service.previa(outro).hash())).isInstanceOf(br.com.servire.api.web.ConflictException.class);
        assertThat(pessoas.count()).isEqualTo(1);
    }
    @Test void confirmacoesConcorrentesEChaveEntreTenantsSaoSeguras() throws Exception {
        limitar("{\"importacoes_mes\":1}");var arquivo=csv("Ana;RESPONSAVEL;;;\n");String hash=service.previa(arquivo).hash();UUID chave=UUID.randomUUID();
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<br.com.servire.api.pessoa.importacao.dto.ImportacaoPessoaDtos.Resultado> tarefa=() -> {TenantContext.set(tenant);try{return service.confirmar(arquivo,chave,hash);}finally{TenantContext.clear();}};
            var a=executor.submit(tarefa);var b=executor.submit(tarefa);var ra=a.get(15,TimeUnit.SECONDS);var rb=b.get(15,TimeUnit.SECONDS);
            assertThat(ra.id()).isEqualTo(rb.id());assertThat(ra.repetida()).isNotEqualTo(rb.repetida());
        }
        assertThat(pessoas.count()).isEqualTo(1);TenantContext.set(nova());
        assertThat(service.confirmar(arquivo,chave,hash).repetida()).isFalse();assertThat(pessoas.count()).isEqualTo(1);
    }
    @Test void csvAspasLimitesEncodingEContatoInvalidos() {
        assertThat(service.previa(csv("\"Ana; Maria\";RESPONSAVEL;;;\n")).podeConfirmar()).isTrue();
        assertThat(service.previa(csv("Ana;RESPONSAVEL;;invalido;\n")).podeConfirmar()).isFalse();
        assertThatThrownBy(() -> service.previa(csv("Ana;RESPONSAVEL;;;\n".repeat(101)))).isInstanceOf(br.com.servire.api.web.BadRequestException.class);
        assertThatThrownBy(() -> service.previa(new MockMultipartFile("arquivo","x.csv","text/csv",new byte[]{(byte)0xff}))).isInstanceOf(br.com.servire.api.web.BadRequestException.class);
    }
    MockMultipartFile planilha(boolean formula,boolean numero) throws Exception {
        try(var wb=new XSSFWorkbook();var saida=new java.io.ByteArrayOutputStream()) {
            wb.createSheet("Vazia");var aba=wb.createSheet("Pessoas");
            var cabecalho=aba.createRow(2);String[] titulos={"Contato","Categoria","Nome da pessoa","Documento","Ignorar"};
            for(int i=0;i<titulos.length;i++) cabecalho.createCell(i).setCellValue(titulos[i]);
            var linha=aba.createRow(3);linha.createCell(0).setCellValue("ana@example.test");linha.createCell(1).setCellValue("RESPONSAVEL");
            if(formula) linha.createCell(2).setCellFormula("\"Ana\"");else linha.createCell(2).setCellValue("Ana");
            if(numero) linha.createCell(3).setCellValue(1234567890d);else linha.createCell(3).setCellValue("01234567890");
            linha.createCell(4).setCellValue("não importar");wb.write(saida);
            return new MockMultipartFile("arquivo","pessoas.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",saida.toByteArray());
        }
    }
    @Test void xlsxMapeadoHttpSelecionaAbaPreservaZerosERepeteSemConsumir() throws Exception {
        var arquivo=planilha(false,false);var opcoes=new Opcoes(1,2,1,3,0,-1);
        mvc.perform(multipart("/pessoas/importacoes/estrutura").file(arquivo).with(csrf()).with(user("criador").authorities(new SimpleGrantedAuthority("PERM_PESSOA"),new SimpleGrantedAuthority("PERM_PESSOA_CRIAR"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.abas[1].nome").value("Pessoas")).andExpect(jsonPath("$.colunas").isEmpty());
        TenantContext.set(tenant);
        mvc.perform(multipart("/pessoas/importacoes/previa").file(arquivo).param("aba","1").param("nome","2").param("papel","1").param("cpf","3").param("email","0").param("telefone","-1")
            .with(csrf()).with(user("criador").authorities(new SimpleGrantedAuthority("PERM_PESSOA"),new SimpleGrantedAuthority("PERM_PESSOA_CRIAR"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.podeConfirmar").value(true)).andExpect(jsonPath("$.linhas[0].linha").value(4));
        TenantContext.set(tenant);var previa=service.previa(arquivo,opcoes);UUID chave=UUID.randomUUID();
        mvc.perform(multipart("/pessoas/importacoes/confirmar").file(arquivo).param("aba","1").param("nome","2").param("papel","1").param("cpf","3").param("email","0").param("telefone","-1")
            .param("chave",chave.toString()).param("hash",previa.hash()).with(csrf()).with(user("criador").authorities(new SimpleGrantedAuthority("PERM_PESSOA"),new SimpleGrantedAuthority("PERM_PESSOA_CRIAR"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.quantidade").value(1));
        TenantContext.set(tenant);assertThat(pessoas.findAll()).singleElement().satisfies(p -> {assertThat(p.getCpf()).isEqualTo("012.345.678-90");assertThat(p.getNomeCompleto()).isEqualTo("Ana");});
        assertThat(service.confirmar(arquivo,chave,previa.hash(),opcoes).repetida()).isTrue();assertThat(registros.count()).isEqualTo(1);
        TenantContext.set(nova());assertThat(service.confirmar(arquivo,chave,previa.hash(),opcoes).repetida()).isFalse();assertThat(pessoas.count()).isEqualTo(1);
    }
    @Test void formulaNumeroEmDocumentoOuMapeamentoAlteradoNaoGravam() throws Exception {
        var opcoes=new Opcoes(1,2,1,3,0,-1);
        assertThat(service.previa(planilha(true,false),opcoes).linhas().getFirst().erro()).contains("Fórmulas");
        assertThat(service.previa(planilha(false,true),opcoes).linhas().getFirst().erro()).contains("texto");
        var arquivo=planilha(false,false);var previa=service.previa(arquivo,opcoes);
        assertThatThrownBy(() -> service.confirmar(arquivo,UUID.randomUUID(),previa.hash(),new Opcoes(1,2,1,-1,0,-1))).isInstanceOf(br.com.servire.api.web.ConflictException.class);
        assertThatThrownBy(() -> service.previa(arquivo,new Opcoes(1,2,2,3,0,-1))).isInstanceOf(br.com.servire.api.web.BadRequestException.class);
        assertThat(pessoas.count()).isZero();assertThat(registros.count()).isZero();
    }
    @Test void csvMapeadoIgnoraExtrasValidaTodosERevalidaDuplicidadesDoBanco() {
        var arquivo=new MockMultipartFile("arquivo","origem.csv","text/csv","E-mail;Nome da pessoa;Categoria;Nota\nana@example.test;Ana;RESPONSAVEL;ok\nerrado;Maria;RESPONSAVEL;ok\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var opcoes=new Opcoes(0,1,2,-1,0,-1);var previa=service.previa(arquivo,opcoes);
        assertThat(previa.linhas()).hasSize(2);assertThat(previa.linhas().get(1).erro()).isNotNull();
        assertThatThrownBy(() -> service.confirmar(arquivo,UUID.randomUUID(),previa.hash(),opcoes)).isInstanceOf(br.com.servire.api.web.BadRequestException.class);assertThat(pessoas.count()).isZero();
        var correto=csv("Ana;RESPONSAVEL;;;\n");var pronta=service.previa(correto);
        service.confirmar(correto,UUID.randomUUID(),pronta.hash());
        assertThatThrownBy(() -> service.confirmar(correto,UUID.randomUUID(),pronta.hash())).isInstanceOf(br.com.servire.api.web.BadRequestException.class);
        assertThat(pessoas.count()).isEqualTo(1);
    }
    @Test void leitorNaoDescobreColunasDoXlsx() throws Exception {
        mvc.perform(multipart("/pessoas/importacoes/estrutura").file(planilha(false,false)).with(csrf()).with(user("leitor").authorities(new SimpleGrantedAuthority("PERM_PESSOA"))))
            .andExpect(status().isForbidden());
    }
}
