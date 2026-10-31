package br.com.republica.model;

import jakarta.persistence.*;

@Entity
public class Morador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    /** Quem sai da casa fica inativo: o histórico de despesas é preservado. */
    @Column(nullable = false)
    private boolean ativo = true;

    /** Quem criou a casa: gerencia moradores, convite e nome da casa. */
    @Column(name = "administrador", nullable = false, columnDefinition = "boolean default false")
    private boolean admin;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Casa casa;

    /**
     * Quando preenchido, este morador paga junto com o responsável (ex.: irmãs que dividem a parte):
     * a parte dele nas despesas é somada à do responsável, e os dois enxergam a dívida do grupo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private Morador responsavel;

    protected Morador() {
    }

    public Morador(String nome, Casa casa) {
        this.nome = nome;
        this.casa = casa;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public Morador getResponsavel() {
        return responsavel;
    }

    public void setResponsavel(Morador responsavel) {
        this.responsavel = responsavel;
    }

    /** Id do "dono" do grupo: o responsável, se houver, ou o próprio morador. */
    public Long idDoGrupo() {
        return responsavel != null ? responsavel.getId() : id;
    }

    public Casa getCasa() {
        return casa;
    }
}
