package br.com.republica;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class EnderecosNoConsole {

    private static final Logger log = LoggerFactory.getLogger(EnderecosNoConsole.class);

    private final int porta;

    public EnderecosNoConsole(@Value("${server.port:8080}") int porta) {
        this.porta = porta;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void mostrar() {
        log.info("""

                República no ar!
                  Aplicação:  http://localhost:{}
                  Swagger:    http://localhost:{}/swagger-ui.html
                  Banco H2:   http://localhost:{}/h2-console
                """, porta, porta, porta);
    }
}