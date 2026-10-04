package br.com.republica.seguranca;

import br.com.republica.model.Morador;
import br.com.republica.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Emite o "crachá" (token JWT) assinado que o front-end envia em cada requisição. */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Duration validade;

    public JwtService(JwtEncoder encoder, @Value("${republica.jwt.validade-horas:12}") long horas) {
        this.encoder = encoder;
        this.validade = Duration.ofHours(horas);
    }

    public String gerar(Usuario usuario) {
        Morador morador = usuario.getMorador();
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("republica")
                .issuedAt(agora)
                .expiresAt(agora.plus(validade))
                .subject(String.valueOf(usuario.getId()))
                .claim("moradorId", String.valueOf(morador.getId()))
                .claim("casaId", String.valueOf(morador.getCasa().getId()))
                .build();
        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
    }
}
