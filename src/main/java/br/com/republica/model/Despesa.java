package br.com.republica.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * Quem participou da despesa, como foi marcado no formulário. Guardado à parte das divisões porque
     * quem paga junto com outro morador não tem divisão própria. Vazio em despesas antigas.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "despesa_participante")
    private Set<Morador> participantes = new LinkedHashSet<>();

    /** Nulo em despesas antigas (equivale a falso). */
    private Boolean cobrarSeparado;

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

    public void removerDivisao(Divisao divisao) {
        divisoes.remove(divisao);
    }

    public void atualizar(String descricao, BigDecimal valorTotal, LocalDate data) {
        this.descricao = descricao;
        this.valorTotal = valorTotal;
        this.data = data;
    }

    public void definirParticipantes(List<Morador> participantes, boolean cobrarSeparado) {
        this.participantes.clear();
        this.participantes.addAll(participantes);
        this.cobrarSeparado = cobrarSeparado;
    }

    /** Verdadeiro se ainda existe alguém devendo nesta despesa. */
    public boolean temPendencias() {
        return divisoes.stream().anyMatch(d -> d.getStatus() != StatusDivisao.PAGA);
    }

    /**
     * Verdadeiro se o grupo do morador (ele e quem paga junto com ele) pagou esta despesa ou tem parte dela.
     * Recebe o id do grupo: o do responsável, ou o do próprio morador quando não há agrupamento.
     */
    public boolean envolve(Long grupoId) {
        return pagador.idDoGrupo().equals(grupoId)
                || divisoes.stream().anyMatch(d -> d.getDevedor().idDoGrupo().equals(grupoId));
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

    public Set<Morador> getParticipantes() {
        return participantes;
    }

    public boolean isCobrarSeparado() {
        return Boolean.TRUE.equals(cobrarSeparado);
    }
}
