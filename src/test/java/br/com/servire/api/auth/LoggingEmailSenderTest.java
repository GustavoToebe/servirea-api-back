package br.com.servire.api.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.assertThat;

class LoggingEmailSenderTest {
    @Test void conviteEResetNuncaRegistramTokenMesmoEmDebug() {
        Logger logger=(Logger) LoggerFactory.getLogger(LoggingEmailSender.class);
        Level anterior=logger.getLevel();var eventos=new ListAppender<ILoggingEvent>();eventos.start();logger.addAppender(eventos);logger.setLevel(Level.DEBUG);
        try {
            var sender=new LoggingEmailSender();
            sender.enviarLinkResetSenha("exemplo@teste.local","http://localhost/reset-password?token=SEGREDO_NAO_LOGAR");
            sender.enviarConvite("exemplo@teste.local","http://localhost/reset-password?token=OUTRO_SEGREDO");
            assertThat(eventos.list).hasSize(2);
            assertThat(eventos.list).allSatisfy(e -> assertThat(e.getFormattedMessage()).doesNotContain("SEGREDO","token=","reset-password?"));
        } finally {logger.detachAppender(eventos);eventos.stop();logger.setLevel(anterior);}
    }
}
