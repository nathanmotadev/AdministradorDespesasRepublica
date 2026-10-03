package br.com.republica.model;

/**
 * Situação de uma parte da despesa que um morador deve a quem pagou.
 */
public enum StatusDivisao {
    /** Ainda não foi cobrada. */
    PENDENTE("Pendente"),
    /** Quem pagou já pediu o dinheiro. */
    COBRADA("Cobrada"),
    /** Quitada (ou a própria parte de quem pagou). */
    PAGA("Paga");

    private final String rotulo;

    StatusDivisao(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
