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

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Casa casa;

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

    public Casa getCasa() {
        return casa;
    }
}
