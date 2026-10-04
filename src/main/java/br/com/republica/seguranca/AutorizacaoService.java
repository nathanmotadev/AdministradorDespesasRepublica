package br.com.republica.seguranca;

import br.com.republica.exception.AcessoNegadoException;
import br.com.republica.model.Morador;
import br.com.republica.repository.MoradorRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Descobre quem está fazendo a requisição e confere se essa pessoa pode mexer naquilo.
 * A identidade vem do token assinado, nunca de um id enviado pelo cliente.
 * O morador é conferido no banco a cada chamada: quem saiu da casa perde o acesso na hora.
 */
@Component
public class AutorizacaoService {

    private final MoradorRepository moradores;

    public AutorizacaoService(MoradorRepository moradores) {
        this.moradores = moradores;
    }

    /** O morador logado, na casa registrada no token. */
    public Morador logado(Jwt jwt) {
        return membroDaCasa(jwt, idDoToken(jwt, "casaId"));
    }

    /** Garante que o logado mora (e está ativo) na casa pedida. */
    public Morador membroDaCasa(Jwt jwt, Long casaId) {
        return moradores.findByIdAndCasaId(idDoToken(jwt, "moradorId"), casaId)
                .filter(Morador::isAtivo)
                .orElseThrow(() -> new AcessoNegadoException("Você não faz parte desta casa."));
    }

    /** Garante que o logado é o administrador da casa pedida. */
    public Morador administradorDaCasa(Jwt jwt, Long casaId) {
        Morador morador = membroDaCasa(jwt, casaId);
        if (!morador.isAdmin()) {
            throw new AcessoNegadoException("Só o administrador da casa pode fazer isso.");
        }
        return morador;
    }

    private Long idDoToken(Jwt jwt, String claim) {
        String valor = jwt.getClaimAsString(claim);
        try {
            return Long.valueOf(valor);
        } catch (NumberFormatException e) {
            throw new AcessoNegadoException("Token inválido.");
        }
    }
}
