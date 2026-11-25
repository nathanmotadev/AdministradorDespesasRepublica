package br.com.republica.service;

import br.com.republica.dto.Dtos.ContaFixaRequest;
import br.com.republica.dto.Dtos.ContaFixaResponse;
import br.com.republica.dto.Dtos.DespesaRequest;
import br.com.republica.dto.Dtos.ItemContaFixaResponse;
import br.com.republica.dto.Dtos.MinhasContasFixasResponse;
import br.com.republica.dto.Dtos.ValorDaContaRequest;
import br.com.republica.exception.AcessoNegadoException;
import br.com.republica.exception.RegraDeNegocioException;
import br.com.republica.exception.RecursoNaoEncontradoException;
import br.com.republica.model.Casa;
import br.com.republica.model.ContaFixa;
import br.com.republica.model.Despesa;
import br.com.republica.model.Divisao;
import br.com.republica.model.StatusDivisao;
import br.com.republica.model.Morador;
import br.com.republica.repository.ContaFixaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Contas que se repetem todo mês. Não há agendador: as despesas vencidas são criadas quando alguém
 * da casa abre o app ({@link #gerarPendentes}), uma por mês vencido, datadas no dia do vencimento.
 * As de valor variável (luz, gás) não são criadas sozinhas: esperam o pagador informar o valor da fatura
 * ({@link #informarValor}).
 * Quem cadastra a conta é o pagador e a única pessoa que pode editar, pausar ou excluir;
 * os demais participantes apenas a enxergam.
 */
@Service
public class ContaFixaService {

    private static final int LIMITE_DE_MESES_POR_VEZ = 12;

    private final ContaFixaRepository contas;
    private final CasaService casaService;
    private final DespesaService despesaService;
    private final GrupoService grupos;
    private final Clock relogio;

    public ContaFixaService(ContaFixaRepository contas, CasaService casaService,
                            DespesaService despesaService, GrupoService grupos, Clock relogio) {
        this.contas = contas;
        this.casaService = casaService;
        this.despesaService = despesaService;
        this.grupos = grupos;
        this.relogio = relogio;
    }

    @Transactional
    public ContaFixaResponse criar(Long casaId, ContaFixaRequest request, Long solicitanteId) {
        Casa casa = casaService.buscarEntidade(casaId);
        Morador pagador = despesaService.buscarMoradorAtivo(casaId, solicitanteId);
        List<Morador> participantes = despesaService.buscarParticipantes(casaId, request.participantesIds());
        ContaFixa conta = new ContaFixa(request.descricao().trim(), valor(request), request.diaDoMes(),
                casa, pagador, participantes, request.variavel(), hoje());
        return ContaFixaResponse.de(contas.save(conta), hoje());
    }

    /** Só as contas em que o solicitante paga ou participa. */
    @Transactional(readOnly = true)
    public List<ContaFixaResponse> listar(Long casaId, Long solicitanteId) {
        casaService.buscarEntidade(casaId);
        LocalDate hoje = hoje();
        return contas.findByCasaIdOrderByDescricaoAsc(casaId).stream()
                .filter(c -> c.envolve(solicitanteId))
                .map(c -> ContaFixaResponse.de(c, hoje))
                .toList();
    }

    /** Quanto o solicitante gasta nas contas fixas no mês atual. */
    @Transactional(readOnly = true)
    public MinhasContasFixasResponse minhaParte(Long casaId, Long solicitanteId) {
        return minhaParte(casaId, solicitanteId, YearMonth.from(hoje()));
    }

    /**
     * Quanto o solicitante (e quem paga junto com ele) gasta nas contas fixas em um mês. Cada um vê só o
     * seu valor: as partes dos demais moradores não aparecem.
     * <p>
     * O que já virou despesa conta pelo valor real daquele mês, que nunca muda depois (editar a conta fixa
     * só vale para as próximas). O que ainda vai vencer no mês conta pela estimativa atual da conta.
     */
    @Transactional(readOnly = true)
    public MinhasContasFixasResponse minhaParte(Long casaId, Long solicitanteId, YearMonth mes) {
        casaService.buscarEntidade(casaId);
        Set<Long> meuGrupo = grupos.idsDoGrupo(casaId, solicitanteId);
        Long meuGrupoId = grupos.idDoGrupo(solicitanteId);
        List<ContaFixa> todas = contas.findByCasaIdOrderByDescricaoAsc(casaId);
        List<ItemContaFixaResponse> itens = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean temEstimado = false;

        // já viraram despesa: valor real
        for (Despesa despesa : despesaService.doMes(casaId, mes)) {
            if (!veioDeContaFixa(despesa, todas) || !despesa.envolve(meuGrupoId)) {
                continue;
            }
            BigDecimal minha = despesa.getDivisoes().stream()
                    .filter(d -> meuGrupo.contains(d.getDevedor().getId()))
                    .map(Divisao::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (minha.signum() > 0) {
                itens.add(new ItemContaFixaResponse(despesa.getDescricao(), minha,
                        quitada(despesa, meuGrupo, meuGrupoId) ? "QUITADA" : "EM_ABERTO"));
                total = total.add(minha);
            }
        }

        // ainda vão vencer neste mês: estimativa da conta
        for (ContaFixa conta : todas) {
            if (!conta.isAtiva() || !conta.envolve(solicitanteId)
                    || !YearMonth.from(conta.getProximoVencimento()).equals(mes)) {
                continue;
            }
            List<Morador> participantes = conta.getParticipantes();
            List<BigDecimal> partes = DivisorDeDespesa.dividir(conta.getValor(), participantes.size());
            BigDecimal minha = BigDecimal.ZERO;
            for (int i = 0; i < participantes.size(); i++) {
                if (meuGrupo.contains(participantes.get(i).getId())) {
                    minha = minha.add(partes.get(i));
                }
            }
            if (minha.signum() > 0) {
                itens.add(new ItemContaFixaResponse(conta.getDescricao(), minha, "A_VENCER"));
                total = total.add(minha);
                temEstimado |= conta.isValorVariavel();
            }
        }
        itens.sort(java.util.Comparator.comparing(ItemContaFixaResponse::valor).reversed());
        return new MinhasContasFixasResponse(total, itens, temEstimado);
    }

    /**
     * Quem pagou a conta considera quitada quando todos já lhe pagaram; quem deve, quando a sua parte
     * (e de quem paga junto com ele) foi paga.
     */
    private boolean quitada(Despesa despesa, Set<Long> meuGrupo, Long meuGrupoId) {
        if (despesa.getPagador().idDoGrupo().equals(meuGrupoId)) {
            return !despesa.temPendencias();
        }
        return despesa.getDivisoes().stream()
                .filter(d -> meuGrupo.contains(d.getDevedor().getId()))
                .allMatch(d -> d.getStatus() == StatusDivisao.PAGA);
    }

    /**
     * Despesas novas trazem o id da conta fixa. As antigas (de antes do vínculo) são reconhecidas pelo
     * conjunto descrição + pagador + dia do vencimento.
     */
    private boolean veioDeContaFixa(Despesa despesa, List<ContaFixa> todas) {
        if (despesa.getContaFixaId() != null) {
            return true;
        }
        return todas.stream().anyMatch(c -> c.getDescricao().equalsIgnoreCase(despesa.getDescricao())
                && c.getPagador().getId().equals(despesa.getPagador().getId())
                && c.getDiaDoMes() == despesa.getData().getDayOfMonth());
    }

    /** Vale para as próximas despesas; as já geradas não mudam. */
    @Transactional
    public ContaFixaResponse editar(Long casaId, Long contaId, ContaFixaRequest request, Long solicitanteId) {
        ContaFixa conta = buscarDoPagador(casaId, contaId, solicitanteId);
        List<Morador> participantes = despesaService.buscarParticipantes(casaId, request.participantesIds());
        conta.atualizar(request.descricao().trim(), valor(request), request.diaDoMes(), participantes,
                request.variavel(), hoje());
        return ContaFixaResponse.de(conta, hoje());
    }

    @Transactional
    public ContaFixaResponse pausar(Long casaId, Long contaId, Long solicitanteId) {
        ContaFixa conta = buscarDoPagador(casaId, contaId, solicitanteId);
        conta.pausar();
        return ContaFixaResponse.de(conta, hoje());
    }

    @Transactional
    public ContaFixaResponse retomar(Long casaId, Long contaId, Long solicitanteId) {
        ContaFixa conta = buscarDoPagador(casaId, contaId, solicitanteId);
        conta.retomar(hoje());
        return ContaFixaResponse.de(conta, hoje());
    }

    /**
     * Cria a despesa do vencimento que estava esperando o valor da fatura e avança a conta para o mês
     * seguinte. O valor informado passa a ser a estimativa do próximo mês. Se houver mais de um mês
     * esperando, cada chamada resolve o mais antigo.
     */
    @Transactional
    public ContaFixaResponse informarValor(Long casaId, Long contaId, ValorDaContaRequest request,
                                           Long solicitanteId) {
        ContaFixa conta = buscarDoPagador(casaId, contaId, solicitanteId);
        LocalDate hoje = hoje();
        if (!conta.aguardandoValor(hoje)) {
            throw new RegraDeNegocioException("Esta conta não está esperando o valor da fatura.");
        }
        BigDecimal valor = request.valor().setScale(2, RoundingMode.HALF_UP);
        if (!registrarDespesa(conta, valor)) {
            throw new RegraDeNegocioException("Não há mais ninguém para dividir esta conta. Edite os participantes.");
        }
        conta.definirValor(valor);
        conta.avancar();
        return ContaFixaResponse.de(conta, hoje);
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
        if (conta.isValorVariavel()) {
            return; // espera o pagador informar o valor da fatura
        }
        int geradas = 0;
        while (!conta.getProximoVencimento().isAfter(hoje) && geradas < LIMITE_DE_MESES_POR_VEZ) {
            if (!registrarDespesa(conta, conta.getValor())) {
                conta.pausar(); // quem pagava saiu da casa ou não sobrou ninguém para dividir
                return;
            }
            conta.avancar();
            geradas++;
        }
    }

    /** Cria a despesa do vencimento atual da conta; falso se não há ninguém para cobrar. */
    private boolean registrarDespesa(ContaFixa conta, BigDecimal valor) {
        Long pagadorId = conta.getPagador().getId();
        List<Long> participantes = conta.getParticipantes().stream()
                .filter(Morador::isAtivo)
                .map(Morador::getId)
                .toList();
        boolean haQuemCobrar = participantes.stream().anyMatch(id -> !id.equals(pagadorId));
        if (!conta.getPagador().isAtivo() || !haQuemCobrar) {
            return false;
        }
        despesaService.registrar(conta.getCasa().getId(),
                new DespesaRequest(conta.getDescricao(), valor, conta.getProximoVencimento(), participantes),
                pagadorId, conta.getId());
        return true;
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
