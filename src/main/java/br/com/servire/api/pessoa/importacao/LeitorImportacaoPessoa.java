package br.com.servire.api.pessoa.importacao;

import br.com.servire.api.pessoa.importacao.dto.ImportacaoPessoaDtos.*;
import br.com.servire.api.web.*;
import org.apache.poi.openxml4j.opc.*;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.*;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.*;

/** Limites antes de construir tabelas POI; planilhas são percorridas por eventos, sem avaliar fórmulas. */
final class LeitorImportacaoPessoa {
    static final List<String> CAMPOS=List.of("nome","papel","cpf","email","telefone");
    record Registro(int linha,List<String> valores,Set<Integer> numericas,String erro) { }
    record Tabela(byte[] bytes,List<Aba> abas,int aba,Registro cabecalho,List<Registro> linhas) {
        Estrutura estrutura() {
            var colunas=new ArrayList<Coluna>();
            for(int i=0;i<cabecalho.valores().size();i++) colunas.add(new Coluna(i,cabecalho.valores().get(i)));
            var sugestao=new LinkedHashMap<String,Integer>();
            for(var campo:CAMPOS) sugestao.put(campo,procurar(cabecalho.valores(),campo));
            return new Estrutura(abas,aba,cabecalho.linha(),colunas,sugestao);
        }
        List<Integer> mapeamento(Opcoes o) {
            var solicitados=Arrays.asList(o.nome(),o.papel(),o.cpf(),o.email(),o.telefone());
            var indices=new ArrayList<Integer>();var usados=new HashSet<Integer>();
            for(int i=0;i<CAMPOS.size();i++) {
                int indice=solicitados.get(i)==null ? procurar(cabecalho.valores(),CAMPOS.get(i)) : solicitados.get(i);
                if(indice < -1 || indice>=cabecalho.valores().size() || i<2 && indice==-1)
                    throw new BadRequestException("Mapeie nome e papel e confira os índices das colunas.");
                if(indice>=0 && !usados.add(indice)) throw new BadRequestException("Uma coluna não pode alimentar dois campos.");
                indices.add(indice);
            }
            return indices;
        }
    }
    private LeitorImportacaoPessoa() { }
    static Tabela ler(MultipartFile arquivo,int aba) {
        if(arquivo==null || arquivo.isEmpty() || arquivo.getSize()>524288)
            throw new BadRequestException("Envie CSV ou XLSX de até 512 KiB.");
        if(aba<0 || aba>=10) throw new BadRequestException("Aba inválida.");
        String nome=Optional.ofNullable(arquivo.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        try {
            byte[] bytes=arquivo.getBytes();
            if(bytes.length>524288) throw new BadRequestException("Arquivo acima de 512 KiB.");
            if(nome.endsWith(".xlsx")) return xlsx(bytes,aba);
            if(!nome.endsWith(".csv") || aba!=0) throw new BadRequestException("Selecione CSV UTF-8 ou XLSX; XLS/XLSM não são aceitos.");
            String texto=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().replaceFirst("^\ufeff","");
            var registros=new ArrayList<Registro>();String[] linhas=texto.split("\r?\n",-1);
            if(linhas.length>1000) throw new BadRequestException("Arquivo com linhas em excesso.");
            char separador=';';boolean primeiro=true;
            for(int i=0;i<linhas.length;i++) {
                if(linhas[i].isBlank()) continue;
                if(primeiro) {separador=separador(linhas[i]);primeiro=false;}
                try {registros.add(new Registro(i+1,ImportacaoPessoaService.campos(linhas[i],separador),Set.of(),null));}
                catch(ApiException ex) {
                    if(registros.isEmpty()) throw ex;
                    registros.add(new Registro(i+1,List.of(),Set.of(),ex.getMessage()));
                }
                if(registros.size()>101) throw new BadRequestException("Máximo de 100 pessoas por lote.");
            }
            return tabela(bytes,List.of(new Aba(0,"CSV")),0,registros);
        } catch(ApiException ex) {throw ex;}
        catch(Exception ex) {throw new BadRequestException("Não foi possível ler o arquivo. Use CSV UTF-8 ou XLSX válido, sem senha.");}
    }
    private static char separador(String linha) {
        // Delimitadores dentro de aspas não determinam o formato.
        boolean aspas=false;int virgula=0,ponto=0;
        for(char c:linha.toCharArray()) {if(c=='"') aspas=!aspas;else if(!aspas) {if(c==';') ponto++;if(c==',') virgula++;}}
        return ponto>=virgula ? ';' : ',';
    }
    private static Tabela tabela(byte[] bytes,List<Aba> abas,int aba,List<Registro> registros) {
        if(registros.isEmpty()) return new Tabela(bytes,List.copyOf(abas),aba,new Registro(0,List.of(),Set.of(),null),List.of());
        Registro cabecalho=registros.getFirst();
        if(cabecalho.erro()!=null || cabecalho.linha()>20 || cabecalho.valores().size()>30)
            throw new BadRequestException("Use cabeçalho nas primeiras 20 linhas, até 30 colunas, sem fórmulas.");
        return new Tabela(bytes,List.copyOf(abas),aba,cabecalho,List.copyOf(registros.subList(1,registros.size())));
    }
    private static int procurar(List<String> colunas,String campo) {
        int encontrado=-1;
        for(int i=0;i<colunas.size();i++) {
            String titulo=Normalizer.normalize(colunas.get(i),Normalizer.Form.NFD).replaceAll("\\p{M}","").trim().toLowerCase(Locale.ROOT);
            titulo=switch(titulo) {case "nome completo" -> "nome";case "e-mail" -> "email";case "celular" -> "telefone";default -> titulo;};
            if(titulo.equals(campo)) {if(encontrado!=-1) return -1;encontrado=i;}
        }
        return encontrado;
    }
    private static Tabela xlsx(byte[] bytes,int aba) throws Exception {
        ZipSeguro.verificar(bytes);
        preflightZip(bytes);
        try(var pacote=OPCPackage.open(new ByteArrayInputStream(bytes))) {
            // Sem relações externas, macros ou objetos embutidos. Não buscar recursos de rede.
            verificarRelacoes(pacote.getRelationships());
            for(var parte:pacote.getParts()) {
                String tipo=parte.getContentType().toLowerCase(Locale.ROOT);
                String caminho=parte.getPartName().getName().toLowerCase(Locale.ROOT);
                if(tipo.contains("macro") || caminho.contains("vbaproject") || caminho.contains("activex") || caminho.contains("embeddings") || caminho.contains("externallinks"))
                    throw new BadRequestException("Remova macros, objetos embutidos e vínculos externos da planilha.");
                if(!parte.isRelationshipPart()) verificarRelacoes(parte.getRelationships());
            }
            var leitor=new XSSFReader(pacote);leitor.setUseReadOnlySharedStringsTable(true);
            var estilos=leitor.getStylesTable();var textos=leitor.getSharedStringsTable();
            var abas=new ArrayList<Aba>();var registros=new ArrayList<Registro>();
            var iterador=(XSSFReader.SheetIterator)leitor.getSheetsData();int indice=0;
            while(iterador.hasNext()) {
                try(var entrada=iterador.next()) {
                    if(indice>=10) throw new BadRequestException("Máximo de dez abas por arquivo.");
                    String nome=iterador.getSheetName();
                    if(nome.length()>100) throw new BadRequestException("Nome de aba muito longo.");
                    abas.add(new Aba(indice,nome));
                    if(indice==aba) {
                        var linhas=new LinhasXlsx(registros);
                        var handler=new XSSFSheetXMLHandler(estilos,null,textos,linhas,new DataFormatter(Locale.ROOT),false);
                        var xml=XMLHelper.newXMLReader();
                        var filtro=new XMLFilterImpl(xml) {
                            int celulas;int ultimaLinha=-1;Set<Integer> referencias=new HashSet<>();
                            @Override public void startElement(String uri,String local,String q,Attributes a) throws SAXException {
                                if(local.equals("row")) {
                                    int numero=Integer.parseInt(a.getValue("r"));
                                    if(numero<1 || numero<=ultimaLinha || numero>10000) throw new BadRequestException("Numeração de linhas inválida ou excessiva.");
                                    ultimaLinha=numero;celulas=0;referencias.clear();
                                }
                                if(local.equals("c")) {
                                    String referencia=a.getValue("r");
                                    if(referencia==null) throw new BadRequestException("Célula sem referência.");
                                    var posicao=new CellReference(referencia);
                                    if(++celulas>30 || posicao.getCol()<0 || !referencias.add((int)posicao.getCol()) || posicao.getCol()>=30 || posicao.getRow()+1!=ultimaLinha)
                                        throw new BadRequestException("Máximo de 30 colunas, sem células repetidas.");
                                    linhas.numero="n".equals(a.getValue("t")) || a.getValue("t")==null;
                                    if("e".equals(a.getValue("t")) || "b".equals(a.getValue("t"))) linhas.erro="Célula com erro ou valor booleano. Use texto.";
                                }
                                if(local.equals("f")) linhas.erro="Fórmulas não são importadas. Substitua por valores em texto.";
                                if(local.equals("mergeCell")) throw new BadRequestException("Desfaça as células mescladas da aba antes de importar.");
                                super.startElement(uri,local,q,a);
                            }
                        };
                        filtro.setContentHandler(handler);filtro.parse(new InputSource(entrada));
                    }
                }
                indice++;
            }
            if(aba>=indice) throw new BadRequestException("Aba inexistente.");
            return tabela(bytes,abas,aba,registros);
        }
    }
    private static void verificarRelacoes(PackageRelationshipCollection relacoes) {
        for(var r:relacoes) if(r.getTargetMode()==TargetMode.EXTERNAL)
            throw new BadRequestException("Remova vínculos externos da planilha antes de importar.");
    }
    private static void preflightZip(byte[] bytes) throws Exception {
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            var nomes=new HashSet<String>();long[] total={0};ZipEntry entrada;
            while((entrada=zip.getNextEntry())!=null) {
                String nome=entrada.getName().toLowerCase(Locale.ROOT);
                if(nomes.size()>=128 || !nomes.add(nome)) throw new BadRequestException("ZIP com entradas repetidas ou excessivas.");
                // Limites também no fluxo local: não confiar apenas no diretório central do ZIP.
                var limitado=new FilterInputStream(zip) {
                    long lidos;
                    private void contar(int quantidade) {
                        if(quantidade>0) {lidos+=quantidade;total[0]+=quantidade;}
                        if(lidos>16L*1024*1024 || total[0]>32L*1024*1024) throw new BadRequestException("Planilha acima do limite descompactado seguro.");
                    }
                    @Override public int read() throws IOException {int valor=in.read();contar(valor<0 ? 0 : 1);return valor;}
                    @Override public int read(byte[] dados,int inicio,int tamanho) throws IOException {int n=in.read(dados,inicio,tamanho);contar(n);return n;}
                    @Override public void close() { /* o dono fecha o ZIP ao fim */ }
                };
                if(nome.endsWith(".xml") || nome.endsWith(".rels")) preflightXml(limitado);
                byte[] buffer=new byte[4096];while(limitado.read(buffer)!=-1) { /* consumir sob limite, sem reter */ }
                zip.closeEntry();
            }
        }
    }
    private static void preflightXml(InputStream entrada) throws Exception {
        var xml=XMLHelper.newXMLReader();
        xml.setErrorHandler(new DefaultHandler() {
            @Override public void error(SAXParseException ex) throws SAXException {throw ex;}
            @Override public void fatalError(SAXParseException ex) throws SAXException {throw ex;}
        });
        xml.setContentHandler(new DefaultHandler() {
            int elementos,profundidade,texto;
            @Override public void startElement(String uri,String local,String q,Attributes a) {
                if(++elementos>20000 || ++profundidade>32 || a.getLength()>30) throw new BadRequestException("XML da planilha acima do limite seguro.");
                if(local.equals("Relationship") && "External".equalsIgnoreCase(a.getValue("TargetMode"))) throw new BadRequestException("Remova vínculos externos da planilha antes de importar.");
                for(int i=0;i<a.getLength();i++) if(a.getValue(i).length()>1000) throw new BadRequestException("Atributo da planilha muito longo.");
            }
            @Override public void endElement(String uri,String local,String q) {profundidade--;}
            @Override public void characters(char[] c,int inicio,int tamanho) {
                texto+=tamanho;if(texto>1048576) throw new BadRequestException("Texto da planilha acima do limite seguro.");
            }
        });
        xml.parse(new InputSource(entrada));
    }
    private static final class LinhasXlsx implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final List<Registro> registros;private List<String> valores;private Set<Integer> numericas;
        private int fisicas;boolean numero;String erro;
        LinhasXlsx(List<Registro> registros) {this.registros=registros;}
        @Override public void startRow(int linha) {
            if(++fisicas>1000) throw new BadRequestException("Arquivo com linhas em excesso.");
            valores=new ArrayList<>();numericas=new HashSet<>();erro=null;
        }
        @Override public void cell(String referencia,String valor,XSSFComment comentario) {
            int coluna=new CellReference(referencia).getCol();
            if(valor.length()>200 || valor.chars().anyMatch(Character::isISOControl)) erro="Célula até 200 caracteres, sem múltiplas linhas ou caracteres de controle.";
            while(valores.size()<=coluna) valores.add("");
            valores.set(coluna,valor.trim());if(numero && !valor.isBlank()) numericas.add(coluna);
        }
        @Override public void endRow(int linha) {
            if(valores.stream().allMatch(String::isBlank) && erro==null) return;
            if(registros.size()>=101) throw new BadRequestException("Máximo de 100 pessoas por lote.");
            registros.add(new Registro(linha+1,List.copyOf(valores),Set.copyOf(numericas),erro));
        }
    }
}
