package br.com.republica.controller;

import br.com.republica.dto.Dtos.CasaRequest;
import br.com.republica.dto.Dtos.CasaResponse;
import br.com.republica.dto.Dtos.ConviteResponse;
import br.com.republica.dto.Dtos.MoradorRequest;
import br.com.republica.dto.Dtos.MoradorResponse;
import br.com.republica.exception.RegraDeNegocioException;
import br.com.republica.model.Morador;
import br.com.republica.seguranca.AutorizacaoService;
import br.com.republica.service.CasaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** A casa é criada no cadastro (/api/auth/cadastro/casa); aqui ficam consulta e gestão. */
@RestController
@RequestMapping("/api/casas")
@Tag(name = "Casas e moradores")
public class CasaController {

    private final CasaService casaService;
    private final AutorizacaoService autorizacao;

    public CasaController(CasaService casaService, AutorizacaoService autorizacao) {
        this.casaService = casaService;
        this.autorizacao = autorizacao;
    }

    @GetMapping("/{casaId}")
    @Operation(summary = "Detalha a minha casa e seus moradores")
    public CasaResponse buscar(@PathVariable Long casaId, @AuthenticationPrincipal Jwt jwt) {
        autorizacao.membroDaCasa(jwt, casaId);
        return casaService.buscar(casaId);
    }

    @PutMapping("/{casaId}")
    @Operation(summary = "Renomeia a casa (administrador)")
    public CasaResponse renomear(@PathVariable Long casaId, @Valid @RequestBody CasaRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        autorizacao.administradorDaCasa(jwt, casaId);
        return casaService.renomear(casaId, request);
    }

    @GetMapping("/{casaId}/convite")
    @Operation(summary = "Mostra o código de convite da casa (administrador)")
    public ConviteResponse convite(@PathVariable Long casaId, @AuthenticationPrincipal Jwt jwt) {
        autorizacao.administradorDaCasa(jwt, casaId);
        return casaService.codigoDeConvite(casaId);
    }

    @PostMapping("/{casaId}/moradores")
    @Operation(summary = "Adiciona um morador sem conta à casa (administrador)")
    public ResponseEntity<MoradorResponse> adicionarMorador(@PathVariable Long casaId,
                                                            @Valid @RequestBody MoradorRequest request,
                                                            @AuthenticationPrincipal Jwt jwt) {
        autorizacao.administradorDaCasa(jwt, casaId);
        MoradorResponse morador = casaService.adicionarMorador(casaId, request);
        return ResponseEntity.created(URI.create("/api/casas/" + casaId + "/moradores/" + morador.id())).body(morador);
    }

    @DeleteMapping("/{casaId}/moradores/{moradorId}")
    @Operation(summary = "Remove um morador da casa (administrador; só sem dívidas em aberto; o histórico é mantido)")
    public ResponseEntity<Void> removerMorador(@PathVariable Long casaId, @PathVariable Long moradorId,
                                               @AuthenticationPrincipal Jwt jwt) {
        Morador administrador = autorizacao.administradorDaCasa(jwt, casaId);
        if (administrador.getId().equals(moradorId)) {
            throw new RegraDeNegocioException("Você não pode remover a si mesmo da casa.");
        }
        casaService.desativarMorador(casaId, moradorId);
        return ResponseEntity.noContent().build();
    }
}
