package br.com.republica.service;

import br.com.republica.dto.Dtos.DespesaRequest;
import br.com.republica.dto.Dtos.DespesaResponse;
import br.com.republica.dto.Dtos.DivisaoResponse;
import br.com.republica.exception.RecursoNaoEncontradoException;
import br.com.republica.exception.RegraDeNegocioException;
import br.com.republica.model.Casa;
import br.com.republica.model.Despesa;
import br.com.republica.model.Divisao;
import br.com.republica.model.Morador;
import br.com.republica.model.StatusDivisao;
import br.com.republica.repository.DespesaRepository;
import br.com.republica.repository.DivisaoRepository;
import br.com.republica.repository.MoradorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

@Service
public class DespesaService {

    private final CasaService casaService;
    private final DespesaRepository despesas;
    private final DivisaoRepository divisoes;
    private final MoradorRepository moradores;

    public DespesaService(CasaService casaService, DespesaRepository despesas,
                          DivisaoRepository divisoes, MoradorRepository moradores) {
        this.casaService = casaService;
        this.despesas = despesas;
        this.divisoes = divisoes;
        this.moradores = moradores;
    }

    /**
     * Registra uma despesa paga por um morador e a divide, em partes iguais,
     * entre os participantes escolhidos (que podem incluir o próprio pagador).
     */
    @Transactional
    public DespesaResponse registrar(Long casaId, DespesaRequest request) {
        Casa casa = casaService.buscarEntidade(casaId);
        Morador pagador = buscarMoradorAtivo(casaId, request.pagadorId());
        List<Morador> participantes = buscarParticipantes(casaId, request.participantesIds());

        boolean alguemDevePagador = participantes.stream().anyMatch(p -> !p.getId().equals(pagador.getId()));
        if (!alguemDevePagador) {
            throw new RegraDeNegocioException(
                    "Selecione ao menos um participante além de quem pagou; senão não há nada a cobrar.");
        }

        LocalDate data = request.data() != null ? request.data() : LocalDate.now();
        BigDecimal total = request.valorTotal().setScale(2, RoundingMode.HALF_UP);
        Despesa despesa = new Despesa(request.descricao().trim(), total, data, casa, pagador);

        List<BigDecimal> partes = DivisorDeDespesa.dividir(total, participantes.size());
        for (int i = 0; i < participantes.size(); i++) {
            Divisao divisao = new Divisao(despesa, participantes.get(i), partes.get(i));
            if (divisao.isParteDoPagador()) {
                divisao.quitar(data);
            }
            despesa.adicionarDivisao(divisao);
        }
        return DespesaResponse.de(despesas.save(despesa));
    }

    /** Lista as despesas da casa; com {@code mes} informado, só as daquele mês. */
    @Transactional(readOnly = true)
    public List<DespesaResponse> listar(Long casaId, YearMonth mes) {
        casaService.buscarEntidade(casaId);
        List<Despesa> encontradas = (mes == null)
                ? despesas.findByCasaIdOrderByDataDescIdDesc(casaId)
                : despesas.findByCasaIdAndDataBetweenOrderByDataDescIdDesc(casaId, mes.atDay(1), mes.atEndOfMonth());
        return encontradas.stream().map(DespesaResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public DespesaResponse buscar(Long casaId, Long despesaId) {
        return DespesaResponse.de(buscarDespesa(casaId, despesaId));
    }

    /** Só é possível excluir se ninguém registrou pagamento ainda (evita perder histórico de dinheiro). */
    @Transactional
    public void excluir(Long casaId, Long despesaId) {
        Despesa despesa = buscarDespesa(casaId, despesaId);
        boolean haPagamentos = despesa.getDivisoes().stream()
                .anyMatch(d -> !d.isParteDoPagador() && d.getStatus() == StatusDivisao.PAGA);
        if (haPagamentos) {
            throw new RegraDeNegocioException("Esta despesa já tem pagamentos registrados e não pode ser excluída.");
        }
        despesas.delete(despesa);
    }

    /** Cobra de uma vez todas as partes em aberto de uma despesa. */
    @Transactional
    public DespesaResponse cobrarTodos(Long casaId, Long despesaId, LocalDate vencimento) {
        validarVencimento(vencimento);
        Despesa despesa = buscarDespesa(casaId, despesaId);
        List<Divisao> emAberto = despesa.getDivisoes().stream()
                .filter(d -> !d.isParteDoPagador() && d.getStatus() != StatusDivisao.PAGA)
                .toList();
        if (emAberto.isEmpty()) {
            throw new RegraDeNegocioException("Não há valores em aberto nesta despesa.");
        }
        emAberto.forEach(d -> d.cobrar(LocalDate.now(), vencimento));
        return DespesaResponse.de(despesa);
    }

    @Transactional
    public DivisaoResponse cobrar(Long divisaoId, LocalDate vencimento) {
        validarVencimento(vencimento);
        Divisao divisao = buscarDivisao(divisaoId);
        if (divisao.isParteDoPagador()) {
            throw new RegraDeNegocioException("A parte de quem pagou não é cobrada.");
        }
        if (divisao.getStatus() == StatusDivisao.PAGA) {
            throw new RegraDeNegocioException("Esta parte já foi paga.");
        }
        divisao.cobrar(LocalDate.now(), vencimento);
        return DivisaoResponse.de(divisao);
    }

    @Transactional
    public DivisaoResponse registrarPagamento(Long divisaoId) {
        Divisao divisao = buscarDivisao(divisaoId);
        if (divisao.getStatus() == StatusDivisao.PAGA) {
            throw new RegraDeNegocioException("Esta parte já foi paga.");
        }
        divisao.quitar(LocalDate.now());
        return DivisaoResponse.de(divisao);
    }

    // ---------- auxiliares ----------

    private Despesa buscarDespesa(Long casaId, Long despesaId) {
        return despesas.findByIdAndCasaId(despesaId, casaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Despesa", despesaId));
    }

    private Divisao buscarDivisao(Long divisaoId) {
        return divisoes.findWithDetalhesById(divisaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Divisão", divisaoId));
    }

    private Morador buscarMoradorAtivo(Long casaId, Long moradorId) {
        return moradores.findByIdAndCasaId(moradorId, casaId)
                .filter(Morador::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Morador", moradorId));
    }

    private List<Morador> buscarParticipantes(Long casaId, List<Long> ids) {
        List<Long> idsUnicos = ids.stream().distinct().toList();
        List<Morador> encontrados = moradores.findByIdInAndCasaId(idsUnicos, casaId).stream()
                .filter(Morador::isAtivo)
                .sorted(Comparator.comparing(Morador::getNome, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (encontrados.size() != idsUnicos.size()) {
            throw new RegraDeNegocioException("Há participantes que não moram (mais) nesta casa.");
        }
        return encontrados;
    }

    private void validarVencimento(LocalDate vencimento) {
        if (vencimento != null && vencimento.isBefore(LocalDate.now())) {
            throw new RegraDeNegocioException("O vencimento não pode ser no passado.");
        }
    }
}
