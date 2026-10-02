package br.com.servire.api.pessoa.importacao;

import br.com.servire.api.pessoa.importacao.dto.ImportacaoPessoaDtos.Opcoes;
import br.com.servire.api.web.BadRequestException;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class LeitorImportacaoPessoaTest {
    MockMultipartFile xlsx(Consumer<XSSFWorkbook> configurar) throws Exception {
        try(var wb=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var aba=wb.createSheet("Pessoas");var h=aba.createRow(0);h.createCell(0).setCellValue("nome");h.createCell(1).setCellValue("papel");
            var linha=aba.createRow(1);linha.createCell(0).setCellValue("Ana");linha.createCell(1).setCellValue("RESPONSAVEL");
            configurar.accept(wb);wb.write(out);return arquivo(out.toByteArray());
        }
    }
    MockMultipartFile arquivo(byte[] bytes) {return new MockMultipartFile("arquivo","x.xlsx","application/octet-stream",bytes);}
    MockMultipartFile substituir(MockMultipartFile arquivo,String nome,String texto) throws Exception {
        try(var in=new ZipInputStream(new ByteArrayInputStream(arquivo.getBytes()));var out=new ByteArrayOutputStream();var zip=new ZipOutputStream(out)) {
            ZipEntry e;while((e=in.getNextEntry())!=null) {zip.putNextEntry(new ZipEntry(e.getName()));zip.write(e.getName().equals(nome) ? texto.getBytes(StandardCharsets.UTF_8) : in.readAllBytes());zip.closeEntry();}
            zip.finish();return arquivo(out.toByteArray());
        }
    }
    @Test void recusaZipFalsoSenhaFormatoAntigoETamanho() {
        assertThatThrownBy(() -> LeitorImportacaoPessoa.ler(arquivo(new byte[]{'P','K',3,4}),0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> LeitorImportacaoPessoa.ler(arquivo(new byte[]{(byte)0xd0,(byte)0xcf,17,0}),0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> LeitorImportacaoPessoa.ler(new MockMultipartFile("arquivo","x.xls","text/csv",new byte[]{1}),0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> LeitorImportacaoPessoa.ler(arquivo(new byte[524289]),0)).isInstanceOf(BadRequestException.class);
    }
    @Test void recusaEntidadeExternaSemLerRecurso() throws Exception {
        var arquivo=substituir(xlsx(w -> {}),"xl/worksheets/sheet1.xml","<?xml version=\"1.0\"?><!DOCTYPE worksheet [<!ENTITY e SYSTEM \"file:///nao-ler\">]><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">&e;</worksheet>");
        assertThatThrownBy(() -> LeitorImportacaoPessoa.ler(arquivo,0)).isInstanceOf(BadRequestException.class);
    }
    @Test void recusaRelacaoExternaEMesclaDeCelulas() throws Exception {
        var externa=xlsx(w -> {var h=w.getCreationHelper().createHyperlink(HyperlinkType.URL);h.setAddress("https://example.test/nao-acessar");w.getSheetAt(0).getRow(1).getCell(0).setHyperlink(h);});
        assertThatThrownBy(() -> LeitorImportacaoPessoa.ler(externa,0)).isInstanceOf(BadRequestException.class).hasMessageContaining("externos");
        var mesclada=xlsx(w -> w.getSheetAt(0).addMergedRegion(new CellRangeAddress(1,1,0,1)));
        assertThatThrownBy(() -> LeitorImportacaoPessoa.ler(mesclada,0)).isInstanceOf(BadRequestException.class).hasMessageContaining("mescladas");
    }
    @Test void limitaLinhasColunasAbasETexto() throws Exception {
        var linhas=xlsx(w -> {for(int i=2;i<=101;i++) w.getSheetAt(0).createRow(i).createCell(0).setCellValue("Pessoa "+i);});
        assertThatThrownBy(() -> LeitorImportacaoPessoa.ler(linhas,0)).isInstanceOf(BadRequestException.class).hasMessageContaining("100 pessoas");
        var colunas=xlsx(w -> w.getSheetAt(0).getRow(0).createCell(30).setCellValue("Extra"));
        assertThatThrownBy(() -> LeitorImportacaoPessoa.ler(colunas,0)).isInstanceOf(BadRequestException.class).hasMessageContaining("30 colunas");
        var abas=xlsx(w -> {for(int i=0;i<10;i++) w.createSheet("Aba "+i);});
        assertThatThrownBy(() -> LeitorImportacaoPessoa.ler(abas,0)).isInstanceOf(BadRequestException.class).hasMessageContaining("dez abas");
        var longa=xlsx(w -> w.getSheetAt(0).getRow(1).getCell(0).setCellValue("a".repeat(201)));
        assertThat(LeitorImportacaoPessoa.ler(longa,0).linhas().getFirst().erro()).contains("200 caracteres");
    }
    @Test void celulasEsparsasNaoDeslocamColunasEOpcionaisPodemSerIgnorados() throws Exception {
        var arquivo=xlsx(w -> {w.getSheetAt(0).getRow(0).createCell(4).setCellValue("e-mail");w.getSheetAt(0).getRow(1).createCell(4).setCellValue("ana@example.test");});
        var tabela=LeitorImportacaoPessoa.ler(arquivo,0);
        assertThat(tabela.estrutura().sugestao().get("email")).isEqualTo(4);assertThat(tabela.linhas().getFirst().valores().get(4)).isEqualTo("ana@example.test");
        assertThat(tabela.mapeamento(new Opcoes(0,0,1,-1,-1,-1))).containsExactly(0,1,-1,-1,-1);
        assertThatThrownBy(() -> tabela.mapeamento(new Opcoes(0,0,1,100,-1,-1))).isInstanceOf(BadRequestException.class);
    }
    @Test void csvComCabecalhoDuplicadoExigeEscolhaExplicita() {
        var arquivo=new MockMultipartFile("arquivo","x.csv","text/csv","nome,nome,papel\nAna,Ana Maria,RESPONSAVEL\n".getBytes(StandardCharsets.UTF_8));
        var tabela=LeitorImportacaoPessoa.ler(arquivo,0);assertThat(tabela.estrutura().sugestao().get("nome")).isEqualTo(-1);
        assertThatThrownBy(() -> tabela.mapeamento(Opcoes.padrao())).isInstanceOf(BadRequestException.class);
        assertThat(tabela.mapeamento(new Opcoes(0,1,2,-1,-1,-1))).containsExactly(1,2,-1,-1,-1);
    }
    @Test void limitaComplexidadeXmlAntesDoPoiERecusaMacroDisfarcada() throws Exception {
        var original=xlsx(w -> {});
        var excessivo=substituir(original,"xl/worksheets/sheet1.xml","<worksheet>"+"<x/>".repeat(20001)+"</worksheet>");
        assertThatThrownBy(() -> LeitorImportacaoPessoa.ler(excessivo,0)).isInstanceOf(BadRequestException.class).hasMessageContaining("limite seguro");
        String tipos;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(original.getBytes()))) {
            ZipEntry entrada;tipos="";while((entrada=zip.getNextEntry())!=null) if(entrada.getName().equals("[Content_Types].xml")) tipos=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
        }
        var macro=substituir(original,"[Content_Types].xml",tipos.replace("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml","application/vnd.ms-excel.sheet.macroEnabled.main+xml"));
        assertThatThrownBy(() -> LeitorImportacaoPessoa.ler(macro,0)).isInstanceOf(BadRequestException.class).hasMessageContaining("macros");
    }
    @Test void celulaRepetidaNaoPodeSobrescreverValorDaMesmaPosicao() throws Exception {
        var repetido=substituir(xlsx(w -> {}),"xl/worksheets/sheet1.xml","<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>nome</t></is></c><c r=\"$A$1\" t=\"inlineStr\"><is><t>papel</t></is></c></row></sheetData></worksheet>");
        assertThatThrownBy(() -> LeitorImportacaoPessoa.ler(repetido,0)).isInstanceOf(BadRequestException.class).hasMessageContaining("repetidas");
    }
}
