package br.com.servire.api.onboarding.dto;
import br.com.servire.api.onboarding.EtapaOnboarding;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
public final class OnboardingDtos {
 private OnboardingDtos(){}
 public enum Acao { CONCLUIR, REABRIR, PULAR }
 public enum Situacao { PENDENTE, PRONTA, CONCLUIDA, DISPENSADA, REVISAR, SEM_PERMISSAO, NAO_CONTRATADA }
 public record Salvar(@NotNull @PositiveOrZero Long versao,@NotNull Acao acao){}
 public record Etapa(EtapaOnboarding codigo,String titulo,String orientacao,String url,String permissao,Situacao situacao,boolean podeConcluir,boolean podeReabrir,boolean podePular){}
 public record Resposta(long versao,List<Etapa> etapas,int concluidas,int total,int percentual,EtapaOnboarding proximaEtapa,Instant iniciadoEm,Instant atualizadoEm){}
}
