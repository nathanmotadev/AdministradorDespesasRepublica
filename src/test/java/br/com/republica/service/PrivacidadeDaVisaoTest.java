package br.com.republica.service;

import br.com.republica.dto.Dtos.CasaRequest;
import br.com.republica.dto.Dtos.DespesaRequest;
import br.com.republica.dto.Dtos.DespesaResponse;
import br.com.republica.dto.Dtos.DivisaoResponse;
import br.com.republica.dto.Dtos.MeuResumoResponse;
import br.com.republica.dto.Dtos.MinhasDividasResponse;
import br.com.republica.dto.Dtos.MoradorRequest;
import br.com.republica.dto.Dtos.MoradorResponse;
import br.com.republica.exception.AcessoNegadoException;
import br.com.republica.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cada morador só enxerga as dívidas em que está envolvido. */
@SpringBootTest
@Transactional
class PrivacidadeDaVisaoTest {

    @Autowired CasaService casaService;
    @Autowired DespesaService despesaService;
    @Autowired PainelService painelService;

    Long casaId;
    MoradorResponse nathan, larissa, matheus, camilly;
    DespesaResponse mercado, luz, pizza;

    @BeforeEach
    void cenario() {
        casaId = casaService.criar(new CasaRequest("Casa da Vila")).id();
        nathan = casaService.adicionarMorador(casaId, new MoradorRequest("Nathan"));
        larissa = casaService.adicionarMorador(casaId, new MoradorRequest("Larissa"));
        matheus = casaService.adicionarMorador(casaId, new MoradorRequest("Matheus"));
        camilly = casaService.adicionarMorador(casaId, new MoradorRequest("Camilly"));

        mercado = despesa("Mercado", "200.00", nathan, nathan, larissa, matheus, camilly);
        luz = despesa("Conta de luz", "240.00", matheus, nathan, larissa, matheus, camilly);
        pizza = despesa("Pizza", "90.00", nathan, nathan, larissa, camilly);
    }

    @Test
    void resumoDaLarissaTemSoOQueEnvolveAElaECompensaOsDoisSentidos() {
        MeuResumoResponse resumo = painelService.meuResumo(casaId, larissa.id());

        // deve 50 (mercado) + 30 (pizza) a Nathan e 60 (luz) a Matheus
        assertThat(resumo.deve()).isEqualByComparingTo("140.00");
        assertThat(resumo.aReceber()).isEqualByComparingTo("0");
        assertThat(resumo.acertos())
                .extracting(a -> a.pessoa().nome())
                .containsExactlyInAnyOrder("Nathan", "Matheus");
        assertThat(resumo.acertos()).allMatch(a -> a.euDevo());
    }

    @Test
    void resumoDoNathanCompensaADividaComOMatheus() {
        MeuResumoResponse resumo = painelService.meuResumo(casaId, nathan.id());

        // Camilly 50 + 30 e Larissa 50 + 30 a receber; com o Matheus: ele deve 50 e Nathan deve 60 -> Nathan paga 10
        assertThat(resumo.aReceber()).isEqualByComparingTo("160.00");
        assertThat(resumo.deve()).isEqualByComparingTo("10.00");
    }

    @Test
    void minhasDividasSeparaOQueDevoDoQueMeDevem() {
        MinhasDividasResponse dividas = painelService.minhasDividas(casaId, nathan.id());

        assertThat(dividas.meDevem()).hasSize(5);
        assertThat(dividas.euDevo()).hasSize(1);
        assertThat(dividas.euDevo().get(0).pessoa().nome()).isEqualTo("Matheus");
    }

    @Test
    void quemNaoParticipouDaPizzaNaoVeAPizza() {
        assertThat(descricoes(despesaService.listar(casaId, null, matheus.id())))
                .doesNotContain("Pizza")
                .contains("Mercado", "Conta de luz");

        assertThat(painelService.minhasDividas(casaId, matheus.id()).euDevo())
                .extracting(d -> d.descricao())
                .doesNotContain("Pizza");

        assertThatThrownBy(() -> despesaService.buscar(casaId, pizza.id(), matheus.id()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void devedorSoVeAPropriaParteDaDespesa() {
        DespesaResponse vistaPelaLarissa = despesaService.buscar(casaId, mercado.id(), larissa.id());

        assertThat(vistaPelaLarissa.divisoes()).hasSize(1);
        assertThat(vistaPelaLarissa.divisoes().get(0).devedor().nome()).isEqualTo("Larissa");

        // quem pagou vê todas as partes
        assertThat(despesaService.buscar(casaId, mercado.id(), nathan.id()).divisoes()).hasSize(4);
    }

    @Test
    void soQuemPagouCobraEConfirmaORecebimento() {
        Long divisaoDaLarissa = divisaoDe(mercado, larissa).id();

        assertThatThrownBy(() -> despesaService.cobrar(divisaoDaLarissa, null, larissa.id()))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> despesaService.registrarPagamento(divisaoDaLarissa, larissa.id()))
                .isInstanceOf(AcessoNegadoException.class);

        // quem não tem relação com a dívida nem sabe que ela existe
        assertThatThrownBy(() -> despesaService.cobrar(divisaoDaLarissa, null, camilly.id()))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        assertThat(despesaService.registrarPagamento(divisaoDaLarissa, nathan.id()).status().name()).isEqualTo("PAGA");
    }

    @Test
    void soQuemPagouExcluiADespesa() {
        assertThatThrownBy(() -> despesaService.excluir(casaId, mercado.id(), larissa.id()))
                .isInstanceOf(AcessoNegadoException.class);
    }

    // ---------- auxiliares ----------

    private DespesaResponse despesa(String descricao, String valor, MoradorResponse pagador, MoradorResponse... participantes) {
        List<Long> ids = Arrays.stream(participantes).map(MoradorResponse::id).toList();
        return despesaService.registrar(casaId, new DespesaRequest(descricao, new BigDecimal(valor), null, ids), pagador.id());
    }

    private List<String> descricoes(List<DespesaResponse> despesas) {
        return despesas.stream().map(DespesaResponse::descricao).toList();
    }

    private DivisaoResponse divisaoDe(DespesaResponse despesa, MoradorResponse morador) {
        return despesa.divisoes().stream().filter(d -> d.devedor().id().equals(morador.id())).findFirst().orElseThrow();
    }
}
