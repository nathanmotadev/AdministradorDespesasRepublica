package br.com.republica.controller;

import br.com.republica.dto.Dtos.MeuResumoResponse;
import br.com.republica.dto.Dtos.MinhasDividasResponse;
import br.com.republica.dto.Dtos.MoradorResponse;
import br.com.republica.dto.Dtos.PerfilResponse;
import br.com.republica.model.Morador;
import br.com.republica.seguranca.AutorizacaoService;
import br.com.republica.service.CasaService;
import br.com.republica.service.PainelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Minha visão": rotas que respondem sempre em nome de quem está logado, sem receber id de morador. */
@RestController
@RequestMapping("/api/eu")
@Tag(name = "Minha visão")
public class EuController {

    private final AutorizacaoService autorizacao;
    private final CasaService casaService;
    private final PainelService painelService;

    public EuController(AutorizacaoService autorizacao, CasaService casaService, PainelService painelService) {
        this.autorizacao = autorizacao;
        this.casaService = casaService;
        this.painelService = painelService;
    }

    @GetMapping
    @Operation(summary = "Quem sou eu e qual é a minha casa")
    public PerfilResponse perfil(@AuthenticationPrincipal Jwt jwt) {
        Morador eu = autorizacao.logado(jwt);
        Long casaId = Long.valueOf(jwt.getClaimAsString("casaId"));
        return new PerfilResponse(MoradorResponse.de(eu), casaService.buscar(casaId));
    }

    @GetMapping("/resumo")
    @Operation(summary = "Quanto eu devo e quanto tenho a receber, por pessoa e já compensado")
    public MeuResumoResponse resumo(@AuthenticationPrincipal Jwt jwt) {
        Morador eu = autorizacao.logado(jwt);
        return painelService.meuResumo(Long.valueOf(jwt.getClaimAsString("casaId")), eu.getId());
    }

    @GetMapping("/dividas")
    @Operation(summary = "As dívidas em aberto em que eu participo: as que devo e as que me devem")
    public MinhasDividasResponse dividas(@AuthenticationPrincipal Jwt jwt) {
        Morador eu = autorizacao.logado(jwt);
        return painelService.minhasDividas(Long.valueOf(jwt.getClaimAsString("casaId")), eu.getId());
    }
}
