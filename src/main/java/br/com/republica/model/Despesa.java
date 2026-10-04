package br.com.republica.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Uma compra/conta paga por um morador (o pagador) e dividida entre os participantes.
 * Cada participante gera uma {@link Divisao}.
 */
@Entity
public class Despesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Column(nullable = false)
    private LocalDate data;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Casa casa;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Morador pagador;

    @OneToMany(mappedBy = "despesa", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Divisao> divisoes = new ArrayList<>();

    protected Despesa() {
    }

    public Despesa(String descricao, BigDecimal valorTotal, LocalDate data, Casa casa, Morador pagador) {
        this.descricao = descricao;
        this.valorTotal = valorTotal;
        this.data = data;
        this.casa = casa;
        this.pagador = pagador;
    }

    public void adicionarDivisao(Divisao divisao) {
        divisoes.add(divisao);
    }

    /** Verdadeiro se ainda existe alguém devendo nesta despesa. */
    public boolean temPendencias() {
        return divisoes.stream().anyMatch(d -> d.getStatus() != StatusDivisao.PAGA);
    }

    /** Verdadeiro se o morador pagou esta despesa ou tem uma parte dela. */
    public boolean envolve(Long moradorId) {
        return pagador.getId().equals(moradorId)
                || divisoes.stream().anyMatch(d -> d.getDevedor().getId().equals(moradorId));
    }

    public Long getId() {
        return id;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public LocalDate getData() {
        return data;
    }

    public Casa getCasa() {
        return casa;
    }

    public Morador getPagador() {
        return pagador;
    }

    public List<Divisao> getDivisoes() {
        return divisoes;
    }
}
