package br.com.republica.service;

import br.com.republica.dto.Dtos.DespesaRequest;
import br.com.republica.dto.Dtos.DespesaResponse;
import br.com.republica.dto.Dtos.DivisaoResponse;
import br.com.republica.exception.AcessoNegadoException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DespesaService {

    private final CasaService casaService;
    private final DespesaRepository despesas;
    private final DivisaoRepository divisoes;
    private final MoradorRepository moradores;
    private final GrupoService grupos;

    public DespesaService(CasaService casaService, DespesaRepository despesas,
                          DivisaoRepository divisoes, MoradorRepository moradores, GrupoService grupos) {
        this.grupos = grupos;
        this.casaService = casaService;
        this.despesas = despesas;
        this.divisoes = divisoes;
        this.moradores = moradores;
    }

    /**
     * Registra uma despesa paga por quem está logado e a divide, em partes iguais,
     * entre os participantes escolhidos (que podem incluir o próprio pagador).
     */
    @Transactional
    public DespesaResponse registrar(Long casaId, DespesaRequest request, Long solicitanteId) {
        Casa casa = casaService.buscarEntidade(casaId);
        Morador pagador = buscarMoradorAtivo(casaId, solicitanteId);
        List<Morador> participantes = buscarParticipantes(casaId, request.participantesIds());

        // quem paga junto com o pagador (ele mesmo e o grupo dele) não deve nada a ele
        Long grupoDoPagador = pagador.idDoGrupo();
        boolean alguemDevePagador = participantes.stream().anyMatch(p -> !p.idDoGrupo().equals(grupoDoPagador));
        if (!alguemDevePagador) {
            throw new RegraDeNegocioException(
                    "Selecione ao menos um participante além de quem pagou; senão não há nada a cobrar.");
        }

        LocalDate data = request.data() != null ? request.data() : LocalDate.now();
        BigDecimal total = request.valorTotal().setScale(2, RoundingMode.HALF_UP);
        Despesa despesa = new Despesa(request.descricao().trim(), total, data, casa, pagador);

        // cada participante tem a sua parte; quem paga junto tem as partes somadas na do responsável
        List<BigDecimal> partes = DivisorDeDespesa.dividir(total, participantes.size());
        Map<Long, Morador> devedores = new LinkedHashMap<>();
        Map<Long, BigDecimal> valores = new LinkedHashMap<>();
        for (int i = 0; i < participantes.size(); i++) {
            Morador participante = participantes.get(i);
            Morador devedor = participante;
            if (participante.idDoGrupo().equals(grupoDoPagador)) {
                devedor = pagador;
            } else if (!request.separar() && participante.getResponsavel() != null) {
                devedor = participante.getResponsavel();
                if (!devedor.isAtivo()) {
                    throw new RegraDeNegocioException("O responsável por " + participante.getNome() + " saiu da casa.");
                }
            }
            devedores.putIfAbsent(devedor.getId(), devedor);
            valores.merge(devedor.getId(), partes.get(i), BigDecimal::add);
        }
        for (Morador devedor : devedores.values()) {
            Divisao divisao = new Divisao(despesa, devedor, valores.get(devedor.getId()));
            if (divisao.isParteDoPagador()) {
                divisao.quitar(data);
            }
            despesa.adicionarDivisao(divisao);
        }
        return DespesaResponse.de(despesas.save(despesa), pagador.idDoGrupo());
    }

    /**
     * Lista as despesas da casa em que o solicitante pagou ou tem parte (as demais não aparecem);
     * com {@code mes} informado, só as daquele mês.
     */
    @Transactional(readOnly = true)
    public List<DespesaResponse> listar(Long casaId, YearMonth mes, Long solicitanteId) {
        casaService.buscarEntidade(casaId);
        List<Despesa> encontradas = (mes == null)
                ? despesas.findByCasaIdOrderByDataDescIdDesc(casaId)
                : despesas.findByCasaIdAndDataBetweenOrderByDataDescIdDesc(casaId, mes.atDay(1), mes.atEndOfMonth());
        Long grupo = grupos.idDoGrupo(solicitanteId);
        return encontradas.stream()
                .filter(d -> d.envolve(grupo))
                .map(d -> DespesaResponse.de(d, grupo))
                .toList();
    }

    @Transactional(readOnly = true)
    public DespesaResponse buscar(Long casaId, Long despesaId, Long solicitanteId) {
        return DespesaResponse.de(despesaVisivel(casaId, despesaId, solicitanteId), grupos.idDoGrupo(solicitanteId));
    }

    /** Só quem pagou exclui, e só se ninguém registrou pagamento ainda (evita perder histórico de dinheiro). */
    @Transactional
    public void excluir(Long casaId, Long despesaId, Long solicitanteId) {
        Despesa despesa = despesaVisivel(casaId, despesaId, solicitanteId);
        exigirPagador(despesa, solicitanteId);
        boolean haPagamentos = despesa.getDivisoes().stream()
                .anyMatch(d -> !d.isParteDoPagador() && d.getStatus() == StatusDivisao.PAGA);
        if (haPagamentos) {
            throw new RegraDeNegocioException("Esta despesa já tem pagamentos registrados e não pode ser excluída.");
        }
        despesas.delete(despesa);
    }

    /** Cobra de uma vez todas as partes em aberto de uma despesa. */
    @Transactional
    public DespesaResponse cobrarTodos(Long casaId, Long despesaId, LocalDate vencimento, Long solicitanteId) {
        validarVencimento(vencimento);
        Despesa despesa = despesaVisivel(casaId, despesaId, solicitanteId);
        exigirPagador(despesa, solicitanteId);
        List<Divisao> emAberto = despesa.getDivisoes().stream()
                .filter(d -> !d.isParteDoPagador() && d.getStatus() != StatusDivisao.PAGA)
                .toList();
        if (emAberto.isEmpty()) {
            throw new RegraDeNegocioException("Não há valores em aberto nesta despesa.");
        }
        emAberto.forEach(d -> d.cobrar(LocalDate.now(), vencimento));
        return DespesaResponse.de(despesa, grupos.idDoGrupo(solicitanteId));
    }

    @Transactional
    public DivisaoResponse cobrar(Long divisaoId, LocalDate vencimento, Long solicitanteId) {
        validarVencimento(vencimento);
        Divisao divisao = divisaoDoPagador(divisaoId, solicitanteId);
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
    public DivisaoResponse registrarPagamento(Long divisaoId, Long solicitanteId) {
        Divisao divisao = divisaoDoPagador(divisaoId, solicitanteId);
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

    /** A despesa só existe para quem pagou ou participa; para os demais, é como se não existisse (404). */
    private Despesa despesaVisivel(Long casaId, Long despesaId, Long solicitanteId) {
        Despesa despesa = buscarDespesa(casaId, despesaId);
        if (!despesa.envolve(grupos.idDoGrupo(solicitanteId))) {
            throw new RecursoNaoEncontradoException("Despesa", despesaId);
        }
        return despesa;
    }

    private void exigirPagador(Despesa despesa, Long solicitanteId) {
        if (!despesa.getPagador().getId().equals(solicitanteId)) {
            throw new AcessoNegadoException("Só quem pagou a despesa pode fazer isso.");
        }
    }

    /**
     * Cobrar e confirmar recebimento são ações de quem pagou. O devedor recebe 403;
     * quem não tem relação nenhuma com a dívida nem fica sabendo que ela existe (404).
     */
    private Divisao divisaoDoPagador(Long divisaoId, Long solicitanteId) {
        Divisao divisao = buscarDivisao(divisaoId);
        boolean ehPagador = divisao.getDespesa().getPagador().getId().equals(solicitanteId);
        boolean ehDevedor = divisao.getDevedor().idDoGrupo().equals(grupos.idDoGrupo(solicitanteId));
        if (!ehPagador && !ehDevedor) {
            throw new RecursoNaoEncontradoException("Divisão", divisaoId);
        }
        if (!ehPagador) {
            throw new AcessoNegadoException("Só quem pagou a despesa pode cobrar ou confirmar o recebimento.");
        }
        return divisao;
    }

    private Divisao buscarDivisao(Long divisaoId) {
        return divisoes.findWithDetalhesById(divisaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Divisão", divisaoId));
    }

    Morador buscarMoradorAtivo(Long casaId, Long moradorId) {
        return moradores.findByIdAndCasaId(moradorId, casaId)
                .filter(Morador::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Morador", moradorId));
    }

    List<Morador> buscarParticipantes(Long casaId, List<Long> ids) {
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
