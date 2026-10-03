package br.com.republica.controller;

import br.com.republica.dto.Dtos.CobrancaRequest;
import br.com.republica.dto.Dtos.DespesaRequest;
import br.com.republica.dto.Dtos.DespesaResponse;
import br.com.republica.service.DespesaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/casas/{casaId}/despesas")
@Tag(name = "Despesas")
public class DespesaController {

    private final DespesaService despesaService;

    public DespesaController(DespesaService despesaService) {
        this.despesaService = despesaService;
    }

    @PostMapping
    @Operation(summary = "Registra uma despesa e divide entre os participantes")
    public ResponseEntity<DespesaResponse> registrar(@PathVariable Long casaId,
                                                     @Valid @RequestBody DespesaRequest request) {
        DespesaResponse despesa = despesaService.registrar(casaId, request);
        return ResponseEntity.created(URI.create("/api/casas/" + casaId + "/despesas/" + despesa.id())).body(despesa);
    }

    @GetMapping
    @Operation(summary = "Lista as despesas da casa, opcionalmente de um mês")
    public List<DespesaResponse> listar(
            @PathVariable Long casaId,
            @Parameter(description = "Mês no formato aaaa-mm", example = "2026-10")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes) {
        return despesaService.listar(casaId, mes);
    }

    @GetMapping("/{despesaId}")
    @Operation(summary = "Detalha uma despesa e suas divisões")
    public DespesaResponse buscar(@PathVariable Long casaId, @PathVariable Long despesaId) {
        return despesaService.buscar(casaId, despesaId);
    }

    @DeleteMapping("/{despesaId}")
    @Operation(summary = "Exclui uma despesa (somente se ninguém pagou ainda)")
    public ResponseEntity<Void> excluir(@PathVariable Long casaId, @PathVariable Long despesaId) {
        despesaService.excluir(casaId, despesaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{despesaId}/cobranca")
    @Operation(summary = "Cobra todos os que ainda devem nesta despesa")
    public DespesaResponse cobrarTodos(@PathVariable Long casaId, @PathVariable Long despesaId,
                                       @RequestBody(required = false) CobrancaRequest request) {
        return despesaService.cobrarTodos(casaId, despesaId, request == null ? null : request.vencimento());
    }
}
