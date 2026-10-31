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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        LocalDate data = request.data() != null ? request.data() : LocalDate.now();
        BigDecimal total = request.valorTotal().setScale(2, RoundingMode.HALF_UP);
        Despesa despesa = new Despesa(request.descricao().trim(), total, data, casa, pagador);
        despesa.definirParticipantes(participantes, request.separar());

        Map<Morador, BigDecimal> partes = calcularPartes(pagador, participantes, total, request.separar());
        partes.forEach((devedor, valor) -> {
            Divisao divisao = new Divisao(despesa, devedor, valor);
            if (divisao.isParteDoPagador()) {
                divisao.quitar(data);
            }
            despesa.adicionarDivisao(divisao);
        });
        return DespesaResponse.de(despesas.save(despesa), pagador.idDoGrupo());
    }

    /**
     * Edita uma despesa a qualquer momento (só quem pagou). A divisão é recalculada: quem continua
     * com o mesmo valor mantém a situação (pendente, cobrada ou paga); quem teve o valor alterado
     * depois de pagar volta a ficar em aberto; quem saiu da despesa deixa de dever; quem entrou começa pendente.
     */
    @Transactional
    public DespesaResponse editar(Long casaId, Long despesaId, DespesaRequest request, Long solicitanteId) {
        Despesa despesa = despesaVisivel(casaId, despesaId, solicitanteId);
        exigirPagador(despesa, solicitanteId);
        Morador pagador = despesa.getPagador();

        // quem já fazia parte da despesa pode continuar nela mesmo que tenha saído da casa
        Set<Long> jaNaDespesa = new HashSet<>();
        despesa.getParticipantes().forEach(m -> jaNaDespesa.add(m.getId()));
        despesa.getDivisoes().forEach(d -> jaNaDespesa.add(d.getDevedor().getId()));
        List<Morador> participantes = buscarParticipantes(casaId, request.participantesIds(), jaNaDespesa);

        LocalDate data = request.data() != null ? request.data() : despesa.getData();
        BigDecimal total = request.valorTotal().setScale(2, RoundingMode.HALF_UP);
        Map<Morador, BigDecimal> partes = calcularPartes(pagador, participantes, total, request.separar());

        despesa.atualizar(request.descricao().trim(), total, data);
        despesa.definirParticipantes(participantes, request.separar());

        Map<Long, Divisao> atuais = new HashMap<>();
        for (Divisao divisao : List.copyOf(despesa.getDivisoes())) {
            boolean continua = partes.keySet().stream().anyMatch(m -> m.getId().equals(divisao.getDevedor().getId()));
            if (continua) {
                atuais.put(divisao.getDevedor().getId(), divisao);
            } else {
                despesa.removerDivisao(divisao);
            }
        }
        partes.forEach((devedor, valor) -> {
            Divisao existente = atuais.get(devedor.getId());
            if (existente != null) {
                existente.alterarValor(valor);
            } else {
                Divisao nova = new Divisao(despesa, devedor, valor);
                if (nova.isParteDoPagador()) {
                    nova.quitar(data);
                }
                despesa.adicionarDivisao(nova);
            }
        });
        return DespesaResponse.de(despesa, pagador.idDoGrupo());
    }

    /**
     * Divide o total em partes iguais entre os participantes. Quem paga junto com outro morador tem a parte
     * somada na do responsável (a não ser que {@code separar}); o grupo do pagador fica com a parte dele.
     */
    private Map<Morador, BigDecimal> calcularPartes(Morador pagador, List<Morador> participantes,
                                                    BigDecimal total, boolean separar) {
        // quem paga junto com o pagador (ele mesmo e o grupo dele) não deve nada a ele
        Long grupoDoPagador = pagador.idDoGrupo();
        boolean alguemDevePagador = participantes.stream().anyMatch(p -> !p.idDoGrupo().equals(grupoDoPagador));
        if (!alguemDevePagador) {
            throw new RegraDeNegocioException(
                    "Selecione ao menos um participante além de quem pagou; senão não há nada a cobrar.");
        }

        List<BigDecimal> partes = DivisorDeDespesa.dividir(total, participantes.size());
        Map<Long, Morador> devedores = new LinkedHashMap<>();
        Map<Long, BigDecimal> valores = new LinkedHashMap<>();
        for (int i = 0; i < participantes.size(); i++) {
            Morador participante = participantes.get(i);
            Morador devedor = participante;
            if (participante.idDoGrupo().equals(grupoDoPagador)) {
                devedor = pagador;
            } else if (!separar && participante.getResponsavel() != null) {
                devedor = participante.getResponsavel();
                if (!devedor.isAtivo()) {
                    throw new RegraDeNegocioException("O responsável por " + participante.getNome() + " saiu da casa.");
                }
            }
            devedores.putIfAbsent(devedor.getId(), devedor);
            valores.merge(devedor.getId(), partes.get(i), BigDecimal::add);
        }
        Map<Morador, BigDecimal> resultado = new LinkedHashMap<>();
        devedores.forEach((id, devedor) -> resultado.put(devedor, valores.get(id)));
        return resultado;
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
        return buscarParticipantes(casaId, ids, Set.of());
    }

    /** Moradores ativos da casa; {@code permitidosInativos} aceita também quem já saiu (usado ao editar). */
    private List<Morador> buscarParticipantes(Long casaId, List<Long> ids, Set<Long> permitidosInativos) {
        List<Long> idsUnicos = ids.stream().distinct().toList();
        List<Morador> encontrados = moradores.findByIdInAndCasaId(idsUnicos, casaId).stream()
                .filter(m -> m.isAtivo() || permitidosInativos.contains(m.getId()))
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
