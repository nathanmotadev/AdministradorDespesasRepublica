package br.com.republica.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DivisorDeDespesaTest {

    @Test
    void divideValorExatoEmPartesIguais() {
        List<BigDecimal> partes = DivisorDeDespesa.dividir(new BigDecimal("200.00"), 4);

        assertThat(partes).containsExactly(
                new BigDecimal("50.00"), new BigDecimal("50.00"), new BigDecimal("50.00"), new BigDecimal("50.00"));
    }

    @Test
    void distribuiOsCentavosQueSobram() {
        List<BigDecimal> partes = DivisorDeDespesa.dividir(new BigDecimal("100.00"), 3);

        assertThat(partes).containsExactly(
                new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
    }

    @Test
    void asPartesSempreSomamOTotal() {
        BigDecimal total = new BigDecimal("1234.57");

        BigDecimal soma = DivisorDeDespesa.dividir(total, 7).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(soma).isEqualByComparingTo(total);
    }

    @Test
    void funcionaComValoresMenoresQueOsParticipantes() {
        List<BigDecimal> partes = DivisorDeDespesa.dividir(new BigDecimal("0.05"), 3);

        assertThat(partes).containsExactly(
                new BigDecimal("0.02"), new BigDecimal("0.02"), new BigDecimal("0.01"));
    }

    @Test
    void rejeitaQuantidadeDePartesInvalida() {
        assertThatThrownBy(() -> DivisorDeDespesa.dividir(new BigDecimal("10.00"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
