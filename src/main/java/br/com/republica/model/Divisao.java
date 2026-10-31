package br.com.republica.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A parte de uma despesa que cabe a um morador.
 * Se o devedor é o próprio pagador, a parte já nasce {@link StatusDivisao#PAGA}.
 */
@Entity
public class Divisao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Despesa despesa;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Morador devedor;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusDivisao status = StatusDivisao.PENDENTE;

    /** Quando o pagador pediu o dinheiro. */
    private LocalDate cobradaEm;

    /** Prazo combinado (opcional). */
    private LocalDate vencimento;

    private LocalDate pagaEm;

    protected Divisao() {
    }

    public Divisao(Despesa despesa, Morador devedor, BigDecimal valor) {
        this.despesa = despesa;
        this.devedor = devedor;
        this.valor = valor;
    }

    public boolean isParteDoPagador() {
        return devedor.getId().equals(despesa.getPagador().getId());
    }

    public void cobrar(LocalDate hoje, LocalDate vencimento) {
        if (status == StatusDivisao.PAGA) {
            throw new IllegalStateException("Esta parte já foi paga.");
        }
        this.status = StatusDivisao.COBRADA;
        this.cobradaEm = hoje;
        this.vencimento = vencimento;
    }

    public void quitar(LocalDate hoje) {
        this.status = StatusDivisao.PAGA;
        this.pagaEm = hoje;
    }

    /**
     * Ajusta o valor depois que a despesa foi editada. Se a parte já estava paga e o valor mudou,
     * ela volta a ficar em aberto para ser acertada de novo (a parte de quem pagou continua paga).
     */
    public void alterarValor(BigDecimal novoValor) {
        if (valor.compareTo(novoValor) == 0) {
            return;
        }
        this.valor = novoValor;
        if (status == StatusDivisao.PAGA && !isParteDoPagador()) {
            this.status = StatusDivisao.PENDENTE;
            this.pagaEm = null;
            this.cobradaEm = null;
            this.vencimento = null;
        }
    }

    public boolean isAtrasada(LocalDate hoje) {
        return status == StatusDivisao.COBRADA && vencimento != null && vencimento.isBefore(hoje);
    }

    public Long getId() {
        return id;
    }

    public Despesa getDespesa() {
        return despesa;
    }

    public Morador getDevedor() {
        return devedor;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public StatusDivisao getStatus() {
        return status;
    }

    public LocalDate getCobradaEm() {
        return cobradaEm;
    }

    public LocalDate getVencimento() {
        return vencimento;
    }

    public LocalDate getPagaEm() {
        return pagaEm;
    }
}
