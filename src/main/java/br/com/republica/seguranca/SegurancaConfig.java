package br.com.republica.seguranca;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * Regras de segurança da aplicação:
 * <ul>
 *   <li>{@code /api/auth/**} é público (cadastro e login);</li>
 *   <li>qualquer outra rota {@code /api/**} exige um token JWT válido (senão, HTTP 401);</li>
 *   <li>arquivos do front-end, Swagger e console do H2 continuam abertos.</li>
 * </ul>
 * A API é stateless: não há sessão no servidor, o token vem em cada requisição.
 */
@Configuration
@EnableWebSecurity
public class SegurancaConfig {

    private final SecretKey chave;

    public SegurancaConfig(@Value("${republica.jwt.segredo}") String segredo) {
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("republica.jwt.segredo precisa ter pelo menos 32 caracteres.");
        }
        this.chave = new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)) // console do H2
                .authorizeHttpRequests(a -> a
                        .requestMatchers(antMatcher("/api/auth/**")).permitAll()
                        .requestMatchers(antMatcher("/api/**")).authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** BCrypt: transforma a senha num hash de mão única, com "sal" aleatório embutido. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
