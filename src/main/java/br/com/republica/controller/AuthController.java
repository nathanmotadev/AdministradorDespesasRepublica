package br.com.republica.controller;

import br.com.republica.dto.Dtos.CadastroComCasaRequest;
import br.com.republica.dto.Dtos.CadastroComConviteRequest;
import br.com.republica.dto.Dtos.LoginRequest;
import br.com.republica.dto.Dtos.SessaoResponse;
import br.com.republica.service.AuthService;
import br.com.republica.service.CadastroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Conta e login")
@SecurityRequirements
public class AuthController {

    private final CadastroService cadastroService;
    private final AuthService authService;

    public AuthController(CadastroService cadastroService, AuthService authService) {
        this.cadastroService = cadastroService;
        this.authService = authService;
    }

    @PostMapping("/cadastro/casa")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma conta e uma casa nova (a pessoa vira a administradora)")
    public SessaoResponse cadastrarComCasa(@Valid @RequestBody CadastroComCasaRequest request) {
        return cadastroService.cadastrarComCasa(request);
    }

    @PostMapping("/cadastro/convite")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma conta e entra numa casa existente com o código de convite")
    public SessaoResponse cadastrarComConvite(@Valid @RequestBody CadastroComConviteRequest request) {
        return cadastroService.cadastrarComConvite(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Entra com e-mail e senha e recebe o token")
    public SessaoResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
