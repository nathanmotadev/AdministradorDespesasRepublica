package br.com.republica.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Casa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    /** Código que o administrador envia a quem vai morar na casa para criar a conta. */
    @Column(unique = true, length = 20)
    private String codigoConvite;

    @OneToMany(mappedBy = "casa")
    @OrderBy("nome")
    private List<Morador> moradores = new ArrayList<>();

    protected Casa() {
    }

    public Casa(String nome, String codigoConvite) {
        this.nome = nome;
        this.codigoConvite = codigoConvite;
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

    public String getCodigoConvite() {
        return codigoConvite;
    }

    public void setCodigoConvite(String codigoConvite) {
        this.codigoConvite = codigoConvite;
    }

    public List<Morador> getMoradores() {
        return moradores;
    }
}
