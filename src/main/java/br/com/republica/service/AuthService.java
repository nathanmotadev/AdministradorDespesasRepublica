package br.com.republica.service;

import br.com.republica.dto.Dtos.LoginRequest;
import br.com.republica.dto.Dtos.MoradorResponse;
import br.com.republica.dto.Dtos.SessaoResponse;
import br.com.republica.exception.CredenciaisInvalidasException;
import br.com.republica.model.Morador;
import br.com.republica.model.Usuario;
import br.com.republica.repository.UsuarioRepository;
import br.com.republica.seguranca.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** E-mail inexistente, senha errada e morador removido dão exatamente o mesmo erro (HTTP 401). */
    @Transactional(readOnly = true)
    public SessaoResponse login(LoginRequest request) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> passwordEncoder.matches(request.senha(), u.getSenhaHash()))
                .filter(u -> u.getMorador().isAtivo())
                .orElseThrow(CredenciaisInvalidasException::new);
        Morador morador = usuario.getMorador();
        return new SessaoResponse(jwtService.gerar(usuario), morador.getCasa().getId(), MoradorResponse.de(morador));
    }
}
