package br.com.servire.api.onboarding;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.time.Instant;
import java.util.UUID;
/** Progresso compartilhado da paróquia. Versão incrementada sob lock da paróquia (V066). */
@Entity @Table(name="onboarding_progresso")
public class OnboardingProgresso {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @TenantId @Column(name="tenant_id",nullable=false) public UUID tenantId;
 @Column(nullable=false) public long versao;
 @Column(nullable=false) public boolean paroquia;
 @Column(nullable=false) public boolean convite;
 @Column(nullable=false) public boolean pessoas;
 @Column(nullable=false) public boolean voluntarios;
 @Column(nullable=false) public boolean escala;
 @Column(name="convite_dispensado",nullable=false) public boolean conviteDispensado;
 @Column(name="iniciado_em",nullable=false) public Instant iniciadoEm;
 @Column(name="atualizado_em",nullable=false) public Instant atualizadoEm;
 public boolean revisada(EtapaOnboarding e){return switch(e){case PAROQUIA->paroquia;case CONVITE->convite;case PESSOAS->pessoas;case VOLUNTARIOS->voluntarios;case ESCALA->escala;};}
 public void revisar(EtapaOnboarding e,boolean valor){switch(e){case PAROQUIA->paroquia=valor;case CONVITE->convite=valor;case PESSOAS->pessoas=valor;case VOLUNTARIOS->voluntarios=valor;case ESCALA->escala=valor;}}
}
