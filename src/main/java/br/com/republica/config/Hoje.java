package br.com.republica.config;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * A data de hoje no fuso da república. {@code LocalDate.now()} usa o fuso do servidor, que na hospedagem
 * costuma ser UTC: depois das 21h em São Paulo o app já enxergaria o dia seguinte.
 */
public final class Hoje {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private Hoje() {
    }

    public static LocalDate hoje() {
        return LocalDate.now(FUSO);
    }
}
