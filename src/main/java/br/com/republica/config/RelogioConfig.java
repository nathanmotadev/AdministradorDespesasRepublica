package br.com.republica.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Relógio injetável: os testes o substituem para simular a passagem dos meses. */
@Configuration
public class RelogioConfig {

    @Bean
    public Clock relogio() {
        return Clock.system(Hoje.FUSO);
    }
}
