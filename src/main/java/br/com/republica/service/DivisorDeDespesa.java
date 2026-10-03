package br.com.republica.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Divide um valor em partes iguais trabalhando em centavos, para que a soma das
 * partes seja sempre exatamente o total (R$ 100,00 / 3 = 33,34 + 33,33 + 33,33).
 */
public final class DivisorDeDespesa {

    private DivisorDeDespesa() {
    }

    public static List<BigDecimal> dividir(BigDecimal total, int quantidadeDePartes) {
        if (quantidadeDePartes <= 0) {
            throw new IllegalArgumentException("A quantidade de partes deve ser positiva.");
        }
        long centavos = total.movePointRight(2).longValueExact();
        long base = centavos / quantidadeDePartes;
        long resto = centavos % quantidadeDePartes;

        List<BigDecimal> partes = new ArrayList<>(quantidadeDePartes);
        for (int i = 0; i < quantidadeDePartes; i++) {
            long parte = base + (i < resto ? 1 : 0);
            partes.add(BigDecimal.valueOf(parte, 2));
        }
        return partes;
    }
}
