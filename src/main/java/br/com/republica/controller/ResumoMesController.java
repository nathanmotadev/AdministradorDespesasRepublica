package br.com.republica.controller;

import br.com.republica.dto.Dtos.ResumoMesResponse;
import br.com.republica.seguranca.AutorizacaoService;
import br.com.republica.service.ResumoMesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/casas/{casaId}/resumo-do-mes")
@Tag(name = "Resumo do mês")
public class ResumoMesController {

    private final ResumoMesService resumoMes;
    private final AutorizacaoService autorizacao;

    public ResumoMesController(ResumoMesService resumoMes, AutorizacaoService autorizacao) {
        this.resumoMes = resumoMes;
        this.autorizacao = autorizacao;
    }

    @GetMapping
    @Operation(summary = "O mês em números: total, comparação com o mês anterior e quanto foi para cada conta",
            description = "Só conta as despesas em que eu paguei ou tenho parte.")
    public ResumoMesResponse resumir(
            @PathVariable Long casaId,
            @Parameter(description = "Mês no formato aaaa-mm; sem ele, vale o mês atual", example = "2026-10")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @AuthenticationPrincipal Jwt jwt) {
        Long eu = autorizacao.membroDaCasa(jwt, casaId).getId();
        return resumoMes.resumir(casaId, mes, eu);
    }
}
