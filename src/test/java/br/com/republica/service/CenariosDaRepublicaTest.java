package br.com.republica.service;

import br.com.republica.dto.Dtos.AcertoResponse;
import br.com.republica.dto.Dtos.CasaRequest;
import br.com.republica.dto.Dtos.DespesaRequest;
import br.com.republica.dto.Dtos.DespesaResponse;
import br.com.republica.dto.Dtos.DivisaoResponse;
import br.com.republica.dto.Dtos.MoradorRequest;
import br.com.republica.dto.Dtos.MoradorResponse;
import br.com.republica.exception.RegraDeNegocioException;
import br.com.republica.model.StatusDivisao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Os cenários do dia a dia de uma república, do jeito que o usuário os descreveria. */
@SpringBootTest
@Transactional
class CenariosDaRepublicaTest {

    @Autowired CasaService casaService;
    @Autowired DespesaService despesaService;
    @Autowired AcertoService acertoService;

    Long casaId;
    MoradorResponse nathan, caio, duda, bia;

    @BeforeEach
    void criarCasaComQuatroMoradores() {
        casaId = casaService.criar(new CasaRequest("Casa da Vila")).id();
        nathan = casaService.adicionarMorador(casaId, new MoradorRequest("Nathan"));
        caio = casaService.adicionarMorador(casaId, new MoradorRequest("Caio"));
        duda = casaService.adicionarMorador(casaId, new MoradorRequest("Duda"));
        bia = casaService.adicionarMorador(casaId, new MoradorRequest("Bia"));
    }

    @Test
    void mercadoDivididoEntreTodos() {
        DespesaResponse mercado = despesa("Mercado", "200.00", nathan, nathan, caio, duda, bia);

        assertThat(mercado.divisoes()).hasSize(4);
        assertThat(mercado.divisoes()).allMatch(d -> d.valor().compareTo(new BigDecimal("50.00")) == 0);
        assertThat(divisaoDe(mercado, nathan).status()).isEqualTo(StatusDivisao.PAGA);
        assertThat(divisaoDe(mercado, caio).status()).isEqualTo(StatusDivisao.PENDENTE);

        assertThat(acertos())
                .extracting(a -> a.de().nome() + ">" + a.para().nome() + ":" + a.valor())
                .containsExactlyInAnyOrder("Bia>Nathan:50.00", "Caio>Nathan:50.00", "Duda>Nathan:50.00");
    }

    @Test
    void pizzaSoComQuemParticipou() {
        DespesaResponse pizza = despesa("Pizza", "90.00", nathan, nathan, caio, bia);

        assertThat(pizza.divisoes()).hasSize(3);
        assertThat(pizza.divisoes()).noneMatch(d -> d.devedor().id().equals(duda.id()));
        assertThat(acertos())
                .extracting(a -> a.de().nome() + ">" + a.para().nome() + ":" + a.valor())
                .containsExactlyInAnyOrder("Bia>Nathan:30.00", "Caio>Nathan:30.00");
    }

    @Test
    void dividasEmSentidosOpostosSaoCompensadas() {
        despesa("Mercado", "200.00", nathan, nathan, caio, duda, bia);        // Duda deve 50 a Nathan
        despesa("Conta de luz", "240.00", duda, nathan, caio, duda, bia);  // Nathan deve 60 a Duda

        List<AcertoResponse> acertos = acertos();

        assertThat(acertos)
                .filteredOn(a -> a.de().id().equals(nathan.id()) && a.para().id().equals(duda.id()))
                .singleElement()
                .satisfies(a -> assertThat(a.valor()).isEqualByComparingTo("10.00"));
        assertThat(acertos).noneMatch(a -> a.de().id().equals(duda.id()) && a.para().id().equals(nathan.id()));
    }

    @Test
    void cobrarEReceberAtualizaOsAcertos() {
        DespesaResponse mercado = despesa("Mercado", "200.00", nathan, nathan, caio, duda, bia);
        Long divisaoDaCaio = divisaoDe(mercado, caio).id();

        DivisaoResponse cobrada = despesaService.cobrar(divisaoDaCaio, LocalDate.now().plusDays(5), nathan.id());
        assertThat(cobrada.status()).isEqualTo(StatusDivisao.COBRADA);
        assertThat(cobrada.vencimento()).isEqualTo(LocalDate.now().plusDays(5));

        DivisaoResponse paga = despesaService.registrarPagamento(divisaoDaCaio, nathan.id());
        assertThat(paga.status()).isEqualTo(StatusDivisao.PAGA);
        assertThat(acertos()).noneMatch(a -> a.de().id().equals(caio.id()));
    }

    @Test
    void naoCobraAParteDeQuemPagou() {
        DespesaResponse mercado = despesa("Mercado", "200.00", nathan, nathan, caio);

        assertThatThrownBy(() -> despesaService.cobrar(divisaoDe(mercado, nathan).id(), null, nathan.id()))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void exigeAoMenosUmParticipanteAlemDoPagador() {
        assertThatThrownBy(() -> despesa("Sozinho", "10.00", nathan, nathan))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoPermiteParticipanteDeOutraCasa() {
        Long outraCasa = casaService.criar(new CasaRequest("Outra casa")).id();
        MoradorResponse estranho = casaService.adicionarMorador(outraCasa, new MoradorRequest("Estranho"));

        assertThatThrownBy(() -> despesa("Mercado", "50.00", nathan, nathan, estranho))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void despesaComPagamentoNaoPodeSerExcluida() {
        DespesaResponse mercado = despesa("Mercado", "200.00", nathan, nathan, caio);
        despesaService.registrarPagamento(divisaoDe(mercado, caio).id(), nathan.id());

        assertThatThrownBy(() -> despesaService.excluir(casaId, mercado.id(), nathan.id()))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void moradorComDividaNaoPodeSair() {
        despesa("Mercado", "200.00", nathan, nathan, caio);

        assertThatThrownBy(() -> casaService.desativarMorador(casaId, caio.id()))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void editarValorEParticipantesRecalculaADivisao() {
        DespesaResponse mercado = despesa("Mercado", "250.00", nathan, nathan, caio);

        DespesaResponse editada = despesaService.editar(casaId, mercado.id(),
                new DespesaRequest("Mercado do mês", new BigDecimal("90.00"), null,
                        List.of(nathan.id(), duda.id(), bia.id())), nathan.id());

        assertThat(editada.descricao()).isEqualTo("Mercado do mês");
        assertThat(editada.divisoes()).noneMatch(d -> d.devedor().id().equals(caio.id()));
        assertThat(acertos())
                .extracting(a -> a.de().nome() + ">" + a.para().nome() + ":" + a.valor())
                .containsExactlyInAnyOrder("Bia>Nathan:30.00", "Duda>Nathan:30.00");
    }

    @Test
    void editarDepoisDePagoMantemQuemNaoMudouEReabreQuemMudou() {
        DespesaResponse mercado = despesa("Mercado", "200.00", nathan, nathan, caio, duda, bia);
        despesaService.registrarPagamento(divisaoDe(mercado, caio).id(), nathan.id());

        // só a descrição muda: a Caio continua quitada
        DespesaResponse soDescricao = despesaService.editar(casaId, mercado.id(),
                new DespesaRequest("Feira", new BigDecimal("200.00"), null,
                        List.of(nathan.id(), caio.id(), duda.id(), bia.id())), nathan.id());
        assertThat(divisaoDe(soDescricao, caio).status()).isEqualTo(StatusDivisao.PAGA);

        // sai a Bia: a parte da Caio sobe para 66,67 e volta a ficar em aberto
        DespesaResponse semBia = despesaService.editar(casaId, mercado.id(),
                new DespesaRequest("Feira", new BigDecimal("200.00"), null,
                        List.of(nathan.id(), caio.id(), duda.id())), nathan.id());
        assertThat(divisaoDe(semBia, caio).status()).isEqualTo(StatusDivisao.PENDENTE);
        assertThat(semBia.divisoes()).hasSize(3);
        assertThat(semBia.participantesIds()).containsExactlyInAnyOrder(nathan.id(), caio.id(), duda.id());
    }

    @Test
    void soQuemPagouEditaADespesa() {
        DespesaResponse mercado = despesa("Mercado", "200.00", nathan, nathan, caio);

        assertThatThrownBy(() -> despesaService.editar(casaId, mercado.id(),
                new DespesaRequest("Mercado", new BigDecimal("10.00"), null, List.of(nathan.id(), caio.id())),
                caio.id()))
                .isInstanceOf(br.com.republica.exception.AcessoNegadoException.class);
    }

    // ---------- auxiliares ----------

    private DespesaResponse despesa(String descricao, String valor, MoradorResponse pagador, MoradorResponse... participantes) {
        List<Long> ids = java.util.Arrays.stream(participantes).map(MoradorResponse::id).toList();
        // quem paga é sempre quem registra
        return despesaService.registrar(casaId,
                new DespesaRequest(descricao, new BigDecimal(valor), null, ids), pagador.id());
    }

    private List<AcertoResponse> acertos() {
        return acertoService.resumo(casaId).acertos();
    }

    private DivisaoResponse divisaoDe(DespesaResponse despesa, MoradorResponse morador) {
        return despesa.divisoes().stream()
                .filter(d -> d.devedor().id().equals(morador.id()))
                .findFirst().orElseThrow();
    }
}
