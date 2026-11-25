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

    /** Casa ou apartamento. Casas criadas antes deste campo ficam nulas no banco e contam como CASA. */
    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private TipoMoradia tipo;

    @OneToMany(mappedBy = "casa")
    @OrderBy("nome")
    private List<Morador> moradores = new ArrayList<>();

    protected Casa() {
    }

    public Casa(String nome, String codigoConvite) {
        this(nome, codigoConvite, TipoMoradia.CASA);
    }

    public Casa(String nome, String codigoConvite, TipoMoradia tipo) {
        this.nome = nome;
        this.codigoConvite = codigoConvite;
        setTipo(tipo);
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

    public TipoMoradia getTipo() {
        return tipo != null ? tipo : TipoMoradia.CASA;
    }

    public void setTipo(TipoMoradia tipo) {
        this.tipo = tipo != null ? tipo : TipoMoradia.CASA;
    }

    public List<Morador> getMoradores() {
        return moradores;
    }
}
