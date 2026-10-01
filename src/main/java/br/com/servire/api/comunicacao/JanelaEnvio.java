package br.com.servire.api.comunicacao;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
/** Coordenação global do provedor de e-mail e por instância WhatsApp; sem conteúdo ou credenciais. */
@Entity @Table(name="fila_envio_janela")
public class JanelaEnvio {
    @Id @Column(length=100) private String id;
    @Column(name="proximo_permitido",nullable=false) private Instant proximoPermitido=Instant.EPOCH;
    @Column(name="reservado_por") private UUID reservadoPor;
    @Column(name="reserva_ate") private Instant reservaAte;
    protected JanelaEnvio() { }
    JanelaEnvio(String id) { this.id=id; }
    boolean disponivel(Instant agora) { return !proximoPermitido.isAfter(agora) && (reservaAte==null || !reservaAte.isAfter(agora)); }
    void reservar(UUID dono,Instant ate,Instant proximo) { reservadoPor=dono; reservaAte=ate; proximoPermitido=proximo; }
    void liberar(UUID dono) { if (dono.equals(reservadoPor)) { reservadoPor=null; reservaAte=null; } }
}
