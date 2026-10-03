package br.com.republica.controller;

import br.com.republica.dto.Dtos.CobrancaRequest;
import br.com.republica.dto.Dtos.DivisaoResponse;
import br.com.republica.service.DespesaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public DivisaoController(DespesaService despesaService) {
        this.despesaService = despesaService;
    }

    @PostMapping("/{divisaoId}/cobranca")
    @Operation(summary = "Cobra a parte de um morador, com prazo opcional")
    public DivisaoResponse cobrar(@PathVariable Long divisaoId,
                                  @RequestBody(required = false) CobrancaRequest request) {
        return despesaService.cobrar(divisaoId, request == null ? null : request.vencimento());
    }

    @PostMapping("/{divisaoId}/pagamento")
    @Operation(summary = "Confirma que a parte foi paga")
    public DivisaoResponse registrarPagamento(@PathVariable Long divisaoId) {
        return despesaService.registrarPagamento(divisaoId);
    }
}
