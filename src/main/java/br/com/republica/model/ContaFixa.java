package br.com.republica.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Uma conta que se repete todo mês (aluguel, internet...). Em cada vencimento vira uma {@link Despesa}
 * paga por quem a cadastrou e dividida entre os participantes.
 * O dia do mês vai de 1 a 28 para existir em todos os meses.
 */
@Entity
public class ContaFixa {

    public static final int DIA_MAXIMO = 28;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private int diaDoMes;

    @Column(nullable = false)
    private boolean ativa = true;

    /** Próxima data em que a despesa deve ser criada; avança um mês a cada despesa gerada. */
    @Column(nullable = false)
    private LocalDate proximoVencimento;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Casa casa;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Morador pagador;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "conta_fixa_participante",
            joinColumns = @JoinColumn(name = "conta_fixa_id"),
            inverseJoinColumns = @JoinColumn(name = "morador_id"))
    private List<Morador> participantes = new ArrayList<>();

    protected ContaFixa() {
    }

    public ContaFixa(String descricao, BigDecimal valor, int diaDoMes, Casa casa, Morador pagador,
                     List<Morador> participantes, LocalDate hoje) {
        this.descricao = descricao;
        this.valor = valor;
        this.diaDoMes = diaDoMes;
        this.casa = casa;
        this.pagador = pagador;
        this.participantes = new ArrayList<>(participantes);
        this.proximoVencimento = primeiroVencimento(diaDoMes, hoje);
    }

    /** Este mês, se o dia ainda não passou; senão, o mês seguinte. */
    static LocalDate primeiroVencimento(int diaDoMes, LocalDate hoje) {
        LocalDate neste = hoje.withDayOfMonth(diaDoMes);
        return neste.isBefore(hoje) ? neste.plusMonths(1) : neste;
    }

    /** Edita os dados que valem para as próximas despesas; as já geradas não mudam. */
    public void atualizar(String descricao, BigDecimal valor, int diaDoMes, List<Morador> participantes) {
        this.descricao = descricao;
        this.valor = valor;
        this.participantes = new ArrayList<>(participantes);
        if (diaDoMes != this.diaDoMes) {
            this.diaDoMes = diaDoMes;
            this.proximoVencimento = proximoVencimento.withDayOfMonth(diaDoMes);
        }
    }

    public void avancar() {
        proximoVencimento = proximoVencimento.plusMonths(1);
    }

    public void pausar() {
        ativa = false;
    }

    /** Ao retomar, os meses em que ficou pausada não são cobrados de uma vez. */
    public void retomar(LocalDate hoje) {
        ativa = true;
        proximoVencimento = primeiroVencimento(diaDoMes, hoje);
    }

    /** Verdadeiro se o morador paga a conta ou participa dela. */
    public boolean envolve(Long moradorId) {
        return pagador.getId().equals(moradorId)
                || participantes.stream().anyMatch(p -> p.getId().equals(moradorId));
    }

    public Long getId() {
        return id;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public int getDiaDoMes() {
        return diaDoMes;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public LocalDate getProximoVencimento() {
        return proximoVencimento;
    }

    public Casa getCasa() {
        return casa;
    }

    public Morador getPagador() {
        return pagador;
    }

    public List<Morador> getParticipantes() {
        return participantes;
    }
}
