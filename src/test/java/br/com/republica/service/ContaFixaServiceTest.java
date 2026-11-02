package br.com.republica.service;

import br.com.republica.dto.Dtos.CasaRequest;
import br.com.republica.dto.Dtos.ContaFixaRequest;
import br.com.republica.dto.Dtos.ContaFixaResponse;
import br.com.republica.dto.Dtos.DespesaResponse;
import br.com.republica.dto.Dtos.MoradorRequest;
import br.com.republica.dto.Dtos.MoradorResponse;
import br.com.republica.exception.AcessoNegadoException;
import br.com.republica.exception.RecursoNaoEncontradoException;
import br.com.republica.repository.ContaFixaRepository;
import br.com.republica.repository.MoradorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contas fixas: geração mensal, edição entre os meses e quem pode mexer. */
@SpringBootTest
@Transactional
class ContaFixaServiceTest {

    static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    @Autowired CasaService casaService;
    @Autowired DespesaService despesaService;
    @Autowired ContaFixaRepository contas;
    @Autowired MoradorRepository moradores;

    Long casaId;
    MoradorResponse nathan, caio, duda;

    @BeforeEach
    void cenario() {
        casaId = casaService.criar(new CasaRequest("Casa da Vila")).id();
        nathan = casaService.adicionarMorador(casaId, new MoradorRequest("Nathan"));
        caio = casaService.adicionarMorador(casaId, new MoradorRequest("Caio"));
        duda = casaService.adicionarMorador(casaId, new MoradorRequest("Duda"));
    }

    ContaFixaService em(String data) {
        Clock relogio = Clock.fixed(Instant.parse(data + "T15:00:00Z"), ZONA);
        return new ContaFixaService(contas, casaService, despesaService, relogio);
    }

    ContaFixaRequest aluguel(String valor, int dia, MoradorResponse... participantes) {
        return new ContaFixaRequest("Aluguel", new BigDecimal(valor), dia,
                java.util.Arrays.stream(participantes).map(MoradorResponse::id).toList());
    }

    List<DespesaResponse> despesasDe(MoradorResponse quem) {
        return despesaService.listar(casaId, null, quem.id());
    }

    @Test
    void naoGeraAntesDoVencimentoEGeraNoDia() {
        ContaFixaService servico = em("2026-10-03");
        ContaFixaResponse conta = servico.criar(casaId, aluguel("900.00", 5, nathan, caio, duda), nathan.id());
        assertThat(conta.proximoVencimento()).isEqualTo(LocalDate.of(2026, 10, 5));

        servico.gerarPendentes(casaId);
        assertThat(despesasDe(nathan)).isEmpty();

        em("2026-10-05").gerarPendentes(casaId);
        List<DespesaResponse> geradas = despesasDe(nathan);
        assertThat(geradas).hasSize(1);
        assertThat(geradas.get(0).data()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(geradas.get(0).valorTotal()).isEqualByComparingTo("900.00");
    }

    @Test
    void naoGeraDuasVezesNoMesmoVencimento() {
        em("2026-10-03").criar(casaId, aluguel("900.00", 5, nathan, caio), nathan.id());

        em("2026-10-06").gerarPendentes(casaId);
        em("2026-10-07").gerarPendentes(casaId);

        assertThat(despesasDe(nathan)).hasSize(1);
    }

    @Test
    void mesesSemAbrirOAppGeramUmaDespesaPorMes() {
        em("2026-07-01").criar(casaId, aluguel("900.00", 10, nathan, caio), nathan.id());

        em("2026-10-15").gerarPendentes(casaId);

        assertThat(despesasDe(nathan)).extracting(DespesaResponse::data)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 10));
    }

    @Test
    void valorEditadoEntreOsMesesVaiSoParaAsProximasDespesas() {
        ContaFixaService setembro = em("2026-09-01");
        ContaFixaResponse conta = setembro.criar(casaId, aluguel("100.00", 5, nathan, caio), nathan.id());
        em("2026-09-06").gerarPendentes(casaId);

        em("2026-09-20").editar(casaId, conta.id(), aluguel("130.00", 5, nathan, caio), nathan.id());
        em("2026-10-06").gerarPendentes(casaId);

        assertThat(despesasDe(nathan)).extracting(DespesaResponse::valorTotal)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("100.00"), new BigDecimal("130.00"));
    }

    @Test
    void contaPausadaNaoGeraEAoRetomarNaoCobraOsMesesParados() {
        ContaFixaResponse conta = em("2026-07-01").criar(casaId, aluguel("100.00", 5, nathan, caio), nathan.id());
        em("2026-07-02").pausar(casaId, conta.id(), nathan.id());

        em("2026-10-01").gerarPendentes(casaId);
        assertThat(despesasDe(nathan)).isEmpty();

        ContaFixaResponse retomada = em("2026-10-01").retomar(casaId, conta.id(), nathan.id());
        assertThat(retomada.proximoVencimento()).isEqualTo(LocalDate.of(2026, 10, 5));
        em("2026-10-06").gerarPendentes(casaId);
        assertThat(despesasDe(nathan)).hasSize(1);
    }

    @Test
    void moradorInativoSaiDaDivisaoEContaSemNinguemParaCobrarFicaPausada() {
        em("2026-10-01").criar(casaId, aluguel("90.00", 5, nathan, caio, duda), nathan.id());
        moradores.findById(duda.id()).orElseThrow().setAtivo(false);

        em("2026-10-06").gerarPendentes(casaId);
        assertThat(despesasDe(nathan).get(0).divisoes()).hasSize(2);

        moradores.findById(caio.id()).orElseThrow().setAtivo(false);
        em("2026-11-06").gerarPendentes(casaId);

        assertThat(despesasDe(nathan)).hasSize(1);
        assertThat(contas.findByCasaIdOrderByDescricaoAsc(casaId)).allMatch(c -> !c.isAtiva());
    }

    @Test
    void soQuemCadastrouAlteraEQuemNaoParticipaNemEnxerga() {
        ContaFixaResponse conta = em("2026-10-01").criar(casaId, aluguel("100.00", 5, nathan, caio), nathan.id());
        ContaFixaService servico = em("2026-10-01");

        assertThatThrownBy(() -> servico.pausar(casaId, conta.id(), caio.id()))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> servico.excluir(casaId, conta.id(), duda.id()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(servico.listar(casaId, caio.id())).hasSize(1);
        assertThat(servico.listar(casaId, duda.id())).isEmpty();
    }

    @Test
    void excluirContaMantemAsDespesasJaGeradas() {
        ContaFixaResponse conta = em("2026-10-01").criar(casaId, aluguel("100.00", 5, nathan, caio), nathan.id());
        em("2026-10-06").gerarPendentes(casaId);

        em("2026-10-07").excluir(casaId, conta.id(), nathan.id());

        assertThat(contas.findByCasaIdOrderByDescricaoAsc(casaId)).isEmpty();
        assertThat(despesasDe(nathan)).hasSize(1);
    }

    @Test
    void diaDoMesAlteradoMudaOProximoVencimento() {
        ContaFixaResponse conta = em("2026-10-01").criar(casaId, aluguel("100.00", 5, nathan, caio), nathan.id());

        ContaFixaResponse editada = em("2026-10-01")
                .editar(casaId, conta.id(), aluguel("100.00", 20, nathan, caio), nathan.id());

        assertThat(editada.proximoVencimento()).isEqualTo(LocalDate.of(2026, 10, 20));
    }
}
