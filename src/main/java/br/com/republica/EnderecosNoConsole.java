package br.com.republica;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class EnderecosNoConsole {

    private static final Logger log = LoggerFactory.getLogger(EnderecosNoConsole.class);

    private final int porta;

    public EnderecosNoConsole(@Value("${server.port:8080}") int porta) {
        this.porta = porta;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void mostrar() {
        StringBuilder celular = new StringBuilder();
        for (String ip : ipsDaRede()) {
            celular.append("\n  No celular: http://").append(ip).append(':').append(porta)
                    .append("  (mesmo Wi-Fi do computador)");
        }
        if (celular.isEmpty()) {
            celular.append("\n  No celular: não achei o IP da rede (o computador está no Wi-Fi?)");
        }
        log.info("""

                República no ar!
                  Aplicação:  http://localhost:{}{}
                  Swagger:    http://localhost:{}/swagger-ui.html
                  Banco H2:   http://localhost:{}/h2-console
                """, porta, celular, porta, porta);
    }

    /** Endereços IPv4 de rede local (Wi-Fi/cabo) deste computador, sem loopback nem interfaces virtuais. */
    private List<String> ipsDaRede() {
        List<String> ips = new ArrayList<>();
        try {
            for (NetworkInterface rede : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!rede.isUp() || rede.isLoopback() || rede.isVirtual() || rede.isPointToPoint()) {
                    continue;
                }
                for (InetAddress endereco : Collections.list(rede.getInetAddresses())) {
                    if (endereco instanceof Inet4Address && endereco.isSiteLocalAddress()) {
                        ips.add(endereco.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Não foi possível listar os endereços de rede", e);
        }
        return ips;
    }
}
