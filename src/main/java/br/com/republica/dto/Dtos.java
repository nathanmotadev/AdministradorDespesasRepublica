package br.com.republica.dto;

import br.com.republica.model.Casa;
import br.com.republica.model.Despesa;
import br.com.republica.model.Divisao;
import br.com.republica.model.Morador;
import br.com.republica.model.StatusDivisao;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
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

    // ---------- entrada: casas, moradores e despesas ----------

    public record CasaRequest(
            @NotBlank(message = "O nome da casa é obrigatório") @Size(max = 80) String nome) {
    }

    public record MoradorRequest(
            @NotBlank(message = "O nome do morador é obrigatório") @Size(max = 80) String nome) {
    }

    /** Quem paga é sempre quem está logado, por isso não há campo "pagador". */
    public record DespesaRequest(
            @NotBlank(message = "A descrição é obrigatória") @Size(max = 120) String descricao,
            @NotNull(message = "O valor é obrigatório")
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero") BigDecimal valorTotal,
            @Schema(description = "Se omitida, usa a data de hoje", example = "2026-10-02") LocalDate data,
            @NotEmpty(message = "Selecione ao menos um participante") List<Long> participantesIds) {
    }

    public record CobrancaRequest(
            @Schema(description = "Prazo combinado para o pagamento (opcional)", example = "2026-10-10")
            LocalDate vencimento) {
    }

    // ---------- entrada: conta e login ----------

    public record CadastroComCasaRequest(
            @NotBlank(message = "O nome da casa é obrigatório") @Size(max = 80) String nomeCasa,
            @NotBlank(message = "Seu nome é obrigatório") @Size(max = 80) String nome,
            @NotBlank(message = "O e-mail é obrigatório") @Email(message = "E-mail inválido") @Size(max = 160) String email,
            @NotBlank(message = "A senha é obrigatória")
            @Size(min = 8, max = 72, message = "A senha deve ter de 8 a 72 caracteres") String senha) {
    }

    public record CadastroComConviteRequest(
            @NotBlank(message = "O código de convite é obrigatório") @Size(max = 20) String codigoConvite,
            @NotBlank(message = "Seu nome é obrigatório") @Size(max = 80) String nome,
            @NotBlank(message = "O e-mail é obrigatório") @Email(message = "E-mail inválido") @Size(max = 160) String email,
            @NotBlank(message = "A senha é obrigatória")
            @Size(min = 8, max = 72, message = "A senha deve ter de 8 a 72 caracteres") String senha) {
    }

    public record LoginRequest(
            @NotBlank(message = "O e-mail é obrigatório") String email,
            @NotBlank(message = "A senha é obrigatória") String senha) {
    }

    // ---------- saída ----------

    public record MoradorResponse(Long id, String nome, boolean ativo, boolean admin) {
        public static MoradorResponse de(Morador m) {
            return new MoradorResponse(m.getId(), m.getNome(), m.isAtivo(), m.isAdmin());
        }
    }

    public record CasaResponse(Long id, String nome, List<MoradorResponse> moradores) {
        public static CasaResponse de(Casa c) {
            return de(c, c.getMoradores());
        }

        public static CasaResponse de(Casa c, List<Morador> moradores) {
            return new CasaResponse(c.getId(), c.getNome(), moradores.stream().map(MoradorResponse::de).toList());
        }
    }

    public record ConviteResponse(String codigo) {
    }

    @Schema(description = "Resposta de cadastro e login: o token deve ir no cabeçalho Authorization: Bearer ...")
    public record SessaoResponse(String token, Long casaId, MoradorResponse morador) {
    }

    public record PerfilResponse(MoradorResponse morador, CasaResponse casa) {
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
        /**
         * Quem pagou vê todas as divisões da despesa; os demais veem apenas a própria parte.
         * É aqui que a privacidade entre moradores é garantida na resposta.
         */
        public static DespesaResponse de(Despesa d, Long visorId) {
            boolean visorPagou = d.getPagador().getId().equals(visorId);
            List<DivisaoResponse> visiveis = d.getDivisoes().stream()
                    .filter(div -> visorPagou || div.getDevedor().getId().equals(visorId))
                    .map(DivisaoResponse::de)
                    .toList();
            return new DespesaResponse(d.getId(), d.getDescricao(), d.getValorTotal(), d.getData(),
                    MoradorResponse.de(d.getPagador()), !d.temPendencias(), visiveis);
        }
    }

    // ---------- saída: visão geral da casa (uso interno e testes) ----------

    @Schema(description = "Quanto cada morador tem a receber e a pagar em toda a casa")
    public record SaldoMoradorResponse(MoradorResponse morador, BigDecimal aReceber, BigDecimal aPagar) {
    }

    @Schema(description = "Quem deve pagar quem, já compensando dívidas nos dois sentidos")
    public record AcertoResponse(MoradorResponse de, MoradorResponse para, BigDecimal valor) {
    }

    public record ResumoResponse(List<SaldoMoradorResponse> saldos, List<AcertoResponse> acertos) {
    }

    // ---------- saída: "Minha visão" ----------

    @Schema(description = "Uma despesa que compõe o acerto. Valor negativo: eu devo; positivo: me devem.")
    public record ItemDeAcertoResponse(String descricao, BigDecimal valor) {
    }

    @Schema(description = "O que o morador logado precisa pagar a (ou receber de) uma pessoa, já compensado")
    public record MeuAcertoResponse(MoradorResponse pessoa, boolean euDevo, BigDecimal valor,
                                    List<ItemDeAcertoResponse> itens) {
    }

    public record MeuResumoResponse(BigDecimal deve, BigDecimal aReceber, List<MeuAcertoResponse> acertos) {
    }

    @Schema(description = "Uma parte em aberto; 'pessoa' é a outra ponta da dívida")
    public record DividaResponse(Long divisaoId, Long despesaId, String descricao, LocalDate data,
                                 MoradorResponse pessoa, BigDecimal valor, StatusDivisao status,
                                 LocalDate vencimento, boolean atrasada) {
    }

    public record MinhasDividasResponse(List<DividaResponse> euDevo, List<DividaResponse> meDevem) {
    }
}
