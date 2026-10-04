package br.com.republica.controller;

import br.com.republica.dto.Dtos.CobrancaRequest;
import br.com.republica.dto.Dtos.DivisaoResponse;
import br.com.republica.seguranca.AutorizacaoService;
import br.com.republica.service.DespesaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/divisoes")
@Tag(name = "Cobranças e pagamentos")
public class DivisaoController {

    private final DespesaService despesaService;
    private final AutorizacaoService autorizacao;

    public DivisaoController(DespesaService despesaService, AutorizacaoService autorizacao) {
        this.despesaService = despesaService;
        this.autorizacao = autorizacao;
    }

    @PostMapping("/{divisaoId}/cobranca")
    @Operation(summary = "Cobra a parte de um morador, com prazo opcional (só quem pagou)")
    public DivisaoResponse cobrar(@PathVariable Long divisaoId,
                                  @RequestBody(required = false) CobrancaRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        Long eu = autorizacao.logado(jwt).getId();
        return despesaService.cobrar(divisaoId, request == null ? null : request.vencimento(), eu);
    }

    @PostMapping("/{divisaoId}/pagamento")
    @Operation(summary = "Confirma que a parte foi paga (só quem pagou a despesa)")
    public DivisaoResponse registrarPagamento(@PathVariable Long divisaoId, @AuthenticationPrincipal Jwt jwt) {
        Long eu = autorizacao.logado(jwt).getId();
        return despesaService.registrarPagamento(divisaoId, eu);
    }
}
