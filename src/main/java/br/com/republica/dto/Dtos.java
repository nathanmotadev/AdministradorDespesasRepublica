package br.com.republica.dto;

import br.com.republica.model.Casa;
import br.com.republica.model.Despesa;
import br.com.republica.model.Divisao;
import br.com.republica.model.Morador;
import br.com.republica.model.StatusDivisao;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Objetos que entram e saem da API (a entidade JPA nunca é exposta diretamente). */
public final class Dtos {

    private Dtos() {
    }

    // ---------- entrada ----------

    public record CasaRequest(
            @NotBlank(message = "O nome da casa é obrigatório") @Size(max = 80) String nome) {
    }

    public record MoradorRequest(
            @NotBlank(message = "O nome do morador é obrigatório") @Size(max = 80) String nome) {
    }

    public record DespesaRequest(
            @NotBlank(message = "A descrição é obrigatória") @Size(max = 120) String descricao,
            @NotNull(message = "O valor é obrigatório")
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero") BigDecimal valorTotal,
            @Schema(description = "Se omitida, usa a data de hoje", example = "2026-10-02") LocalDate data,
            @NotNull(message = "Informe quem pagou") Long pagadorId,
            @NotEmpty(message = "Selecione ao menos um participante") List<Long> participantesIds) {
    }

    public record CobrancaRequest(
            @Schema(description = "Prazo combinado para o pagamento (opcional)", example = "2026-10-10")
            LocalDate vencimento) {
    }

    // ---------- saída ----------

    public record MoradorResponse(Long id, String nome, boolean ativo) {
        public static MoradorResponse de(Morador m) {
            return new MoradorResponse(m.getId(), m.getNome(), m.isAtivo());
        }
    }

    public record CasaResponse(Long id, String nome, List<MoradorResponse> moradores) {
        public static CasaResponse de(Casa c) {
            return new CasaResponse(c.getId(), c.getNome(),
                    c.getMoradores().stream().map(MoradorResponse::de).toList());
        }
    }

    public record DivisaoResponse(Long id, MoradorResponse devedor, BigDecimal valor, StatusDivisao status,
                                  boolean parteDoPagador, LocalDate cobradaEm, LocalDate vencimento,
                                  LocalDate pagaEm, boolean atrasada) {
        public static DivisaoResponse de(Divisao d) {
            return new DivisaoResponse(d.getId(), MoradorResponse.de(d.getDevedor()), d.getValor(), d.getStatus(),
                    d.isParteDoPagador(), d.getCobradaEm(), d.getVencimento(), d.getPagaEm(),
                    d.isAtrasada(LocalDate.now()));
        }
    }

    public record DespesaResponse(Long id, String descricao, BigDecimal valorTotal, LocalDate data,
                                  MoradorResponse pagador, boolean quitada, List<DivisaoResponse> divisoes) {
        public static DespesaResponse de(Despesa d) {
            return new DespesaResponse(d.getId(), d.getDescricao(), d.getValorTotal(), d.getData(),
                    MoradorResponse.de(d.getPagador()), !d.temPendencias(),
                    d.getDivisoes().stream().map(DivisaoResponse::de).toList());
        }
    }

    @Schema(description = "Quanto cada morador tem a receber e a pagar em toda a casa")
    public record SaldoMoradorResponse(MoradorResponse morador, BigDecimal aReceber, BigDecimal aPagar) {
    }

    @Schema(description = "Quem deve pagar quem, já compensando dívidas nos dois sentidos")
    public record AcertoResponse(MoradorResponse de, MoradorResponse para, BigDecimal valor) {
    }

    public record ResumoResponse(List<SaldoMoradorResponse> saldos, List<AcertoResponse> acertos) {
    }
}
