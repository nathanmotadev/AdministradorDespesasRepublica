package br.com.republica.service;

import br.com.republica.dto.Dtos.ContaFixaRequest;
import br.com.republica.dto.Dtos.ContaFixaResponse;
import br.com.republica.dto.Dtos.DespesaRequest;
import br.com.republica.exception.AcessoNegadoException;
import br.com.republica.exception.RecursoNaoEncontradoException;
import br.com.republica.model.Casa;
import br.com.republica.model.ContaFixa;
import br.com.republica.model.Morador;
import br.com.republica.repository.ContaFixaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Contas que se repetem todo mês. Não há agendador: as despesas vencidas são criadas quando alguém
 * da casa abre o app ({@link #gerarPendentes}), uma por mês vencido, datadas no dia do vencimento.
 * Quem cadastra a conta é o pagador e a única pessoa que pode editar, pausar ou excluir;
 * os demais participantes apenas a enxergam.
 */
@Service
public class ContaFixaService {

    private static final int LIMITE_DE_MESES_POR_VEZ = 12;

    private final ContaFixaRepository contas;
    private final CasaService casaService;
    private final DespesaService despesaService;
    private final Clock relogio;

    public ContaFixaService(ContaFixaRepository contas, CasaService casaService,
                            DespesaService despesaService, Clock relogio) {
        this.contas = contas;
        this.casaService = casaService;
        this.despesaService = despesaService;
        this.relogio = relogio;
    }

    @Transactional
    public ContaFixaResponse criar(Long casaId, ContaFixaRequest request, Long solicitanteId) {
        Casa casa = casaService.buscarEntidade(casaId);
        Morador pagador = despesaService.buscarMoradorAtivo(casaId, solicitanteId);
        List<Morador> participantes = despesaService.buscarParticipantes(casaId, request.participantesIds());
        ContaFixa conta = new ContaFixa(request.descricao().trim(), valor(request), request.diaDoMes(),
                casa, pagador, participantes, hoje());
        return ContaFixaResponse.de(contas.save(conta));
    }

    /** Só as contas em que o solicitante paga ou participa. */
    @Transactional(readOnly = true)
    public List<ContaFixaResponse> listar(Long casaId, Long solicitanteId) {
        casaService.buscarEntidade(casaId);
        return contas.findByCasaIdOrderByDescricaoAsc(casaId).stream()
                .filter(c -> c.envolve(solicitanteId))
                .map(ContaFixaResponse::de)
                .toList();
    }

    /** Vale para as próximas despesas; as já geradas não mudam. */
    @Transactional
    public ContaFixaResponse editar(Long casaId, Long contaId, ContaFixaRequest request, Long solicitanteId) {
        ContaFixa conta = buscarDoPagador(casaId, contaId, solicitanteId);
        List<Morador> participantes = despesaService.buscarParticipantes(casaId, request.participantesIds());
        conta.atualizar(request.descricao().trim(), valor(request), request.diaDoMes(), participantes);
        return ContaFixaResponse.de(conta);
    }

    @Transactional
    public ContaFixaResponse pausar(Long casaId, Long contaId, Long solicitanteId) {
        ContaFixa conta = buscarDoPagador(casaId, contaId, solicitanteId);
        conta.pausar();
        return ContaFixaResponse.de(conta);
    }

    @Transactional
    public ContaFixaResponse retomar(Long casaId, Long contaId, Long solicitanteId) {
        ContaFixa conta = buscarDoPagador(casaId, contaId, solicitanteId);
        conta.retomar(hoje());
        return ContaFixaResponse.de(conta);
    }

    /** Despesas já geradas continuam no histórico. */
    @Transactional
    public void excluir(Long casaId, Long contaId, Long solicitanteId) {
        contas.delete(buscarDoPagador(casaId, contaId, solicitanteId));
    }

    /**
     * Cria as despesas das contas vencidas da casa. A consulta sem trava evita bloquear a cada acesso;
     * a trava de escrita impede que dois acessos simultâneos gerem a mesma despesa duas vezes.
     */
    @Transactional
    public void gerarPendentes(Long casaId) {
        LocalDate hoje = hoje();
        if (!contas.existsByCasaIdAndAtivaTrueAndProximoVencimentoLessThanEqual(casaId, hoje)) {
            return;
        }
        for (ContaFixa conta : contas.findByCasaIdAndAtivaTrue(casaId)) {
            gerar(conta, hoje);
        }
    }

    private void gerar(ContaFixa conta, LocalDate hoje) {
        int geradas = 0;
        while (!conta.getProximoVencimento().isAfter(hoje) && geradas < LIMITE_DE_MESES_POR_VEZ) {
            Long pagadorId = conta.getPagador().getId();
            List<Long> participantes = conta.getParticipantes().stream()
                    .filter(Morador::isAtivo)
                    .map(Morador::getId)
                    .toList();
            boolean haQuemCobrar = participantes.stream().anyMatch(id -> !id.equals(pagadorId));
            if (!conta.getPagador().isAtivo() || !haQuemCobrar) {
                conta.pausar(); // quem pagava saiu da casa ou não sobrou ninguém para dividir
                return;
            }
            despesaService.registrar(conta.getCasa().getId(),
                    new DespesaRequest(conta.getDescricao(), conta.getValor(),
                            conta.getProximoVencimento(), participantes),
                    pagadorId);
            conta.avancar();
            geradas++;
        }
    }

    /** 404 para quem não está na conta (esconde que existe); 403 para quem só participa. */
    private ContaFixa buscarDoPagador(Long casaId, Long contaId, Long solicitanteId) {
        ContaFixa conta = contas.findByIdAndCasaId(contaId, casaId)
                .filter(c -> c.envolve(solicitanteId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta fixa", contaId));
        if (!conta.getPagador().getId().equals(solicitanteId)) {
            throw new AcessoNegadoException("Só quem cadastrou a conta fixa pode alterá-la.");
        }
        return conta;
    }

    private BigDecimal valor(ContaFixaRequest request) {
        return request.valor().setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio);
    }
}
