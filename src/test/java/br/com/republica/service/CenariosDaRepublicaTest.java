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
    MoradorResponse nathan, larissa, matheus, camilly;

    @BeforeEach
    void criarCasaComQuatroMoradores() {
        casaId = casaService.criar(new CasaRequest("Casa da Vila")).id();
        nathan = casaService.adicionarMorador(casaId, new MoradorRequest("Nathan"));
        larissa = casaService.adicionarMorador(casaId, new MoradorRequest("Larissa"));
        matheus = casaService.adicionarMorador(casaId, new MoradorRequest("Matheus"));
        camilly = casaService.adicionarMorador(casaId, new MoradorRequest("Camilly"));
    }

    @Test
    void mercadoDivididoEntreTodos() {
        DespesaResponse mercado = despesa("Mercado", "200.00", nathan, nathan, larissa, matheus, camilly);

        assertThat(mercado.divisoes()).hasSize(4);
        assertThat(mercado.divisoes()).allMatch(d -> d.valor().compareTo(new BigDecimal("50.00")) == 0);
        assertThat(divisaoDe(mercado, nathan).status()).isEqualTo(StatusDivisao.PAGA);
        assertThat(divisaoDe(mercado, larissa).status()).isEqualTo(StatusDivisao.PENDENTE);

        assertThat(acertos())
                .extracting(a -> a.de().nome() + ">" + a.para().nome() + ":" + a.valor())
                .containsExactlyInAnyOrder("Camilly>Nathan:50.00", "Larissa>Nathan:50.00", "Matheus>Nathan:50.00");
    }

    @Test
    void pizzaSoComQuemParticipou() {
        DespesaResponse pizza = despesa("Pizza", "90.00", nathan, nathan, larissa, camilly);

        assertThat(pizza.divisoes()).hasSize(3);
        assertThat(pizza.divisoes()).noneMatch(d -> d.devedor().id().equals(matheus.id()));
        assertThat(acertos())
                .extracting(a -> a.de().nome() + ">" + a.para().nome() + ":" + a.valor())
                .containsExactlyInAnyOrder("Camilly>Nathan:30.00", "Larissa>Nathan:30.00");
    }

    @Test
    void dividasEmSentidosOpostosSaoCompensadas() {
        despesa("Mercado", "200.00", nathan, nathan, larissa, matheus, camilly);        // Matheus deve 50 a Nathan
        despesa("Conta de luz", "240.00", matheus, nathan, larissa, matheus, camilly);  // Nathan deve 60 a Matheus

        List<AcertoResponse> acertos = acertos();

        assertThat(acertos)
                .filteredOn(a -> a.de().id().equals(nathan.id()) && a.para().id().equals(matheus.id()))
                .singleElement()
                .satisfies(a -> assertThat(a.valor()).isEqualByComparingTo("10.00"));
        assertThat(acertos).noneMatch(a -> a.de().id().equals(matheus.id()) && a.para().id().equals(nathan.id()));
    }

    @Test
    void cobrarEReceberAtualizaOsAcertos() {
        DespesaResponse mercado = despesa("Mercado", "200.00", nathan, nathan, larissa, matheus, camilly);
        Long divisaoDaLarissa = divisaoDe(mercado, larissa).id();

        DivisaoResponse cobrada = despesaService.cobrar(divisaoDaLarissa, LocalDate.now().plusDays(5), nathan.id());
        assertThat(cobrada.status()).isEqualTo(StatusDivisao.COBRADA);
        assertThat(cobrada.vencimento()).isEqualTo(LocalDate.now().plusDays(5));

        DivisaoResponse paga = despesaService.registrarPagamento(divisaoDaLarissa, nathan.id());
        assertThat(paga.status()).isEqualTo(StatusDivisao.PAGA);
        assertThat(acertos()).noneMatch(a -> a.de().id().equals(larissa.id()));
    }

    @Test
    void naoCobraAParteDeQuemPagou() {
        DespesaResponse mercado = despesa("Mercado", "200.00", nathan, nathan, larissa);

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
        DespesaResponse mercado = despesa("Mercado", "200.00", nathan, nathan, larissa);
        despesaService.registrarPagamento(divisaoDe(mercado, larissa).id(), nathan.id());

        assertThatThrownBy(() -> despesaService.excluir(casaId, mercado.id(), nathan.id()))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void moradorComDividaNaoPodeSair() {
        despesa("Mercado", "200.00", nathan, nathan, larissa);

        assertThatThrownBy(() -> casaService.desativarMorador(casaId, larissa.id()))
                .isInstanceOf(RegraDeNegocioException.class);
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
