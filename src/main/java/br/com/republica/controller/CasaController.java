package br.com.republica.controller;

import br.com.republica.dto.Dtos.CasaRequest;
import br.com.republica.dto.Dtos.CasaResponse;
import br.com.republica.dto.Dtos.MoradorRequest;
import br.com.republica.dto.Dtos.MoradorResponse;
import br.com.republica.dto.Dtos.ResumoResponse;
import br.com.republica.service.AcertoService;
import br.com.republica.service.CasaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/casas")
@Tag(name = "Casas e moradores")
public class CasaController {

    private final CasaService casaService;
    private final AcertoService acertoService;

    public CasaController(CasaService casaService, AcertoService acertoService) {
        this.casaService = casaService;
        this.acertoService = acertoService;
    }

    @PostMapping
    @Operation(summary = "Cria uma casa")
    public ResponseEntity<CasaResponse> criar(@Valid @RequestBody CasaRequest request) {
        CasaResponse casa = casaService.criar(request);
        return ResponseEntity.created(URI.create("/api/casas/" + casa.id())).body(casa);
    }
    @PutMapping("/{casaId}")
    @Operation(summary = "Renomeia uma casa")
    public CasaResponse renomear(@PathVariable Long casaId, @Valid @RequestBody CasaRequest request){
        return casaService.renomear(casaId, request);
    }



    @GetMapping
    @Operation(summary = "Lista as casas")
    public List<CasaResponse> listar() {
        return casaService.listar();
    }

    @GetMapping("/{casaId}")
    @Operation(summary = "Detalha uma casa e seus moradores")
    public CasaResponse buscar(@PathVariable Long casaId) {
        return casaService.buscar(casaId);
    }

    @PostMapping("/{casaId}/moradores")
    @Operation(summary = "Adiciona um morador à casa")
    public ResponseEntity<MoradorResponse> adicionarMorador(@PathVariable Long casaId,
                                                            @Valid @RequestBody MoradorRequest request) {
        MoradorResponse morador = casaService.adicionarMorador(casaId, request);
        return ResponseEntity.created(URI.create("/api/casas/" + casaId + "/moradores/" + morador.id())).body(morador);
    }

    @DeleteMapping("/{casaId}/moradores/{moradorId}")
    @Operation(summary = "Remove um morador da casa (só sem dívidas em aberto; o histórico é mantido)")
    public ResponseEntity<Void> removerMorador(@PathVariable Long casaId, @PathVariable Long moradorId) {
        casaService.desativarMorador(casaId, moradorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{casaId}/resumo")
    @Operation(summary = "Quem deve quanto a quem, com compensação entre dívidas opostas")
    public ResumoResponse resumo(@PathVariable Long casaId) {
        return acertoService.resumo(casaId);
    }
}
