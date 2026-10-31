package br.com.republica.controller;

import br.com.republica.dto.Dtos.ContaFixaRequest;
import br.com.republica.dto.Dtos.ContaFixaResponse;
import br.com.republica.seguranca.AutorizacaoService;
import br.com.republica.service.ContaFixaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/casas/{casaId}/contas-fixas")
@Tag(name = "Contas fixas")
public class ContaFixaController {

    private final ContaFixaService service;
    private final AutorizacaoService autorizacao;

    public ContaFixaController(ContaFixaService service, AutorizacaoService autorizacao) {
        this.service = service;
        this.autorizacao = autorizacao;
    }

    @PostMapping
    @Operation(summary = "Cadastra uma conta que se repete todo mês; quem cadastra é o pagador")
    public ResponseEntity<ContaFixaResponse> criar(@PathVariable Long casaId,
                                                   @Valid @RequestBody ContaFixaRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        Long eu = autorizacao.membroDaCasa(jwt, casaId).getId();
        ContaFixaResponse criada = service.criar(casaId, request, eu);
        return ResponseEntity.created(URI.create("/api/casas/" + casaId + "/contas-fixas/" + criada.id()))
                .body(criada);
    }

    @GetMapping
    @Operation(summary = "Lista as contas fixas em que você paga ou participa")
    public List<ContaFixaResponse> listar(@PathVariable Long casaId, @AuthenticationPrincipal Jwt jwt) {
        return service.listar(casaId, autorizacao.membroDaCasa(jwt, casaId).getId());
    }

    @PutMapping("/{contaId}")
    @Operation(summary = "Altera valor, dia ou participantes; vale a partir da próxima cobrança")
    public ContaFixaResponse editar(@PathVariable Long casaId, @PathVariable Long contaId,
                                    @Valid @RequestBody ContaFixaRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return service.editar(casaId, contaId, request, autorizacao.membroDaCasa(jwt, casaId).getId());
    }

    @PostMapping("/{contaId}/pausar")
    @Operation(summary = "Pausa a conta: nenhuma despesa é criada até retomar")
    public ContaFixaResponse pausar(@PathVariable Long casaId, @PathVariable Long contaId,
                                    @AuthenticationPrincipal Jwt jwt) {
        return service.pausar(casaId, contaId, autorizacao.membroDaCasa(jwt, casaId).getId());
    }

    @PostMapping("/{contaId}/retomar")
    @Operation(summary = "Retoma uma conta pausada, a partir do próximo vencimento")
    public ContaFixaResponse retomar(@PathVariable Long casaId, @PathVariable Long contaId,
                                     @AuthenticationPrincipal Jwt jwt) {
        return service.retomar(casaId, contaId, autorizacao.membroDaCasa(jwt, casaId).getId());
    }

    @DeleteMapping("/{contaId}")
    @Operation(summary = "Exclui a conta fixa; as despesas já geradas permanecem")
    public ResponseEntity<Void> excluir(@PathVariable Long casaId, @PathVariable Long contaId,
                                        @AuthenticationPrincipal Jwt jwt) {
        service.excluir(casaId, contaId, autorizacao.membroDaCasa(jwt, casaId).getId());
        return ResponseEntity.noContent().build();
    }
}
