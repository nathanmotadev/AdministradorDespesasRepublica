package br.com.republica.model;

import jakarta.persistence.*;

/**
 * A conta de acesso de um morador. Guarda o e-mail e o <em>hash</em> da senha
 * (nunca a senha em si) e aponta para o {@link Morador}, que é quem participa das dívidas.
 */
@Entity
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(nullable = false)
    private String senhaHash;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    private Morador morador;

    protected Usuario() {
    }

    public Usuario(String email, String senhaHash, Morador morador) {
        this.email = email.trim().toLowerCase();
        this.senhaHash = senhaHash;
        this.morador = morador;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Morador getMorador() {
        return morador;
    }
}
