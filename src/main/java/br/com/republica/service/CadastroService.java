package br.com.republica.service;

import br.com.republica.dto.Dtos.CadastroComCasaRequest;
import br.com.republica.dto.Dtos.CadastroComConviteRequest;
import br.com.republica.dto.Dtos.MoradorResponse;
import br.com.republica.dto.Dtos.SessaoResponse;
import br.com.republica.exception.RegraDeNegocioException;
import br.com.republica.model.Casa;
import br.com.republica.model.Morador;
import br.com.republica.model.Usuario;
import br.com.republica.repository.CasaRepository;
import br.com.republica.repository.MoradorRepository;
import br.com.republica.repository.UsuarioRepository;
import br.com.republica.seguranca.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Cria contas. Há dois caminhos: abrir uma casa nova (quem abre vira administrador)
 * ou entrar numa casa que já existe, com o código de convite.
 */
@Service
public class CadastroService {

    private final CasaService casaService;
    private final CasaRepository casas;
    private final MoradorRepository moradores;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public CadastroService(CasaService casaService, CasaRepository casas, MoradorRepository moradores,
                           UsuarioRepository usuarios, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.casaService = casaService;
        this.casas = casas;
        this.moradores = moradores;
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public SessaoResponse cadastrarComCasa(CadastroComCasaRequest request) {
        exigirEmailLivre(request.email());
        Casa casa = casaService.criarEntidade(request.nomeCasa());
        Morador administrador = new Morador(request.nome().trim(), casa);
        administrador.setAdmin(true);
        return abrirSessao(administrador, request.email(), request.senha());
    }

    @Transactional
    public SessaoResponse cadastrarComConvite(CadastroComConviteRequest request) {
        exigirEmailLivre(request.email());
        String codigo = request.codigoConvite().trim().toUpperCase(Locale.ROOT);
        Casa casa = casas.findByCodigoConvite(codigo)
                .orElseThrow(() -> new RegraDeNegocioException("Código de convite inválido."));

        String nome = request.nome().trim();
        if (moradores.existsByCasaIdAndNomeIgnoreCaseAndAtivoTrue(casa.getId(), nome)) {
            throw new RegraDeNegocioException("Já existe um morador chamado " + nome
                    + " nesta casa. Use outro nome ou peça ao administrador para ajustar.");
        }
        return abrirSessao(new Morador(nome, casa), request.email(), request.senha());
    }

    private SessaoResponse abrirSessao(Morador morador, String email, String senha) {
        moradores.save(morador);
        Usuario usuario = usuarios.save(new Usuario(email, passwordEncoder.encode(senha), morador));
        return new SessaoResponse(jwtService.gerar(usuario), morador.getCasa().getId(), MoradorResponse.de(morador));
    }

    private void exigirEmailLivre(String email) {
        if (usuarios.existsByEmailIgnoreCase(email.trim())) {
            throw new RegraDeNegocioException("Já existe uma conta com este e-mail.");
        }
    }
}
