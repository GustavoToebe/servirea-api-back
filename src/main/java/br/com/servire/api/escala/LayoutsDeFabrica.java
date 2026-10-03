package br.com.servire.api.escala;

/**
 * Layouts de sistema criados em toda paróquia nova (a V050 cria os mesmos para as que já existiam).
 * O JSON é o do editor visual: elementos do documento (título, subtítulo) e da celebração (data e vagas),
 * cada um com linha, coluna e largura na grade de 12 colunas. Mudou aqui, muda a V050 só se ainda não foi aplicada.
 */
public final class LayoutsDeFabrica {
  private LayoutsDeFabrica() {}

  public static final String NOME_SEMANAL = "Padrão Semanal";
  public static final String DESCRICAO_SEMANAL =
      "Tabela de dias úteis com Acólito Missal, Cruz, Credência, 2 Velas e 2 Sinos.";
  public static final String COLUNAS_SEMANAL =
      """
      [{"idLocal": "c-tit","tipo": "TITULO","escopo": "DOCUMENTO","linha": 0,"coluna": 0,"largura": 12,"conteudo": "#TITULO_ESCALA#","alinhamento": "center","ordem": 1},{"idLocal": "c-sub","tipo": "SUBTITULO","escopo": "DOCUMENTO","linha": 1,"coluna": 0,"largura": 12,"conteudo": "#MES_ANO#","alinhamento": "center","ordem": 2},{"idLocal": "c-dt","tipo": "DATA","escopo": "CELEBRACAO","linha": 0,"coluna": 0,"largura": 2,"conteudo": "#DATA_HORA#","alinhamento": "left","ordem": 3},{"idLocal": "v-mis","tipo": "VAGA","escopo": "CELEBRACAO","linha": 0,"coluna": 1,"largura": 2,"funcao": "MISSAL","posicao": 1,"rotulo": "Acólito Missal","ordem": 4},{"idLocal": "v-crz","tipo": "VAGA","escopo": "CELEBRACAO","linha": 0,"coluna": 2,"largura": 2,"funcao": "CRUZ","posicao": 1,"rotulo": "Cruz","ordem": 5},{"idLocal": "v-crd","tipo": "VAGA","escopo": "CELEBRACAO","linha": 0,"coluna": 3,"largura": 2,"funcao": "CREDENCIA","posicao": 1,"rotulo": "Credência","ordem": 6},{"idLocal": "v-vl1","tipo": "VAGA","escopo": "CELEBRACAO","linha": 0,"coluna": 4,"largura": 1,"funcao": "VELA","posicao": 1,"rotulo": "Vela 1","ordem": 7},{"idLocal": "v-vl2","tipo": "VAGA","escopo": "CELEBRACAO","linha": 0,"coluna": 5,"largura": 1,"funcao": "VELA","posicao": 2,"rotulo": "Vela 2","ordem": 8},{"idLocal": "v-sn1","tipo": "VAGA","escopo": "CELEBRACAO","linha": 0,"coluna": 6,"largura": 1,"funcao": "SINO","posicao": 1,"rotulo": "Sino 1","ordem": 9},{"idLocal": "v-sn2","tipo": "VAGA","escopo": "CELEBRACAO","linha": 0,"coluna": 7,"largura": 1,"funcao": "SINO","posicao": 2,"rotulo": "Sino 2","ordem": 10}]\
      """;

  public static final String NOME_MENSAL = "Padrão Mensal";
  public static final String DESCRICAO_MENSAL =
      "Formato clássico com Missal, Cruz, Credência, 2 Velas, 4 Coletas e 2 Sinos.";
  public static final String COLUNAS_MENSAL =
      """
      [{"idLocal": "c-tit","tipo": "TITULO","escopo": "DOCUMENTO","linha": 0,"coluna": 0,"largura": 12,"conteudo": "#TITULO_ESCALA#","alinhamento": "center","ordem": 1},{"idLocal": "c-sub","tipo": "SUBTITULO","escopo": "DOCUMENTO","linha": 1,"coluna": 0,"largura": 12,"conteudo": "#MES_ANO#","alinhamento": "center","ordem": 2},{"idLocal": "c-dt","tipo": "DATA","escopo": "CELEBRACAO","linha": 0,"coluna": 0,"largura": 12,"conteudo": "#DATA_HORA#","alinhamento": "left","ordem": 3},{"idLocal": "v-mis","tipo": "VAGA","escopo": "CELEBRACAO","linha": 1,"coluna": 0,"largura": 1,"funcao": "MISSAL","posicao": 1,"rotulo": "Acólito Missal","ordem": 4},{"idLocal": "v-crz","tipo": "VAGA","escopo": "CELEBRACAO","linha": 1,"coluna": 1,"largura": 1,"funcao": "CRUZ","posicao": 1,"rotulo": "Cruz","ordem": 5},{"idLocal": "v-crd","tipo": "VAGA","escopo": "CELEBRACAO","linha": 1,"coluna": 2,"largura": 1,"funcao": "CREDENCIA","posicao": 1,"rotulo": "Credência","ordem": 6},{"idLocal": "v-vl1","tipo": "VAGA","escopo": "CELEBRACAO","linha": 1,"coluna": 3,"largura": 1,"funcao": "VELA","posicao": 1,"rotulo": "Vela 1","ordem": 7},{"idLocal": "v-vl2","tipo": "VAGA","escopo": "CELEBRACAO","linha": 2,"coluna": 0,"largura": 1,"funcao": "VELA","posicao": 2,"rotulo": "Vela 2","ordem": 8},{"idLocal": "v-cl1","tipo": "VAGA","escopo": "CELEBRACAO","linha": 2,"coluna": 1,"largura": 1,"funcao": "COLETA","posicao": 1,"rotulo": "Coleta 1","ordem": 9},{"idLocal": "v-cl2","tipo": "VAGA","escopo": "CELEBRACAO","linha": 2,"coluna": 2,"largura": 1,"funcao": "COLETA","posicao": 2,"rotulo": "Coleta 2","ordem": 10},{"idLocal": "v-cl3","tipo": "VAGA","escopo": "CELEBRACAO","linha": 2,"coluna": 3,"largura": 1,"funcao": "COLETA","posicao": 3,"rotulo": "Coleta 3","ordem": 11},{"idLocal": "v-cl4","tipo": "VAGA","escopo": "CELEBRACAO","linha": 3,"coluna": 0,"largura": 1,"funcao": "COLETA","posicao": 4,"rotulo": "Coleta 4","ordem": 12},{"idLocal": "v-sn1","tipo": "VAGA","escopo": "CELEBRACAO","linha": 3,"coluna": 1,"largura": 1,"funcao": "SINO","posicao": 1,"rotulo": "Sino 1","ordem": 13},{"idLocal": "v-sn2","tipo": "VAGA","escopo": "CELEBRACAO","linha": 3,"coluna": 2,"largura": 1,"funcao": "SINO","posicao": 2,"rotulo": "Sino 2","ordem": 14}]\
      """;
}
