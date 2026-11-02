package br.com.republica.service;

import br.com.republica.dto.Dtos.CasaRequest;
import br.com.republica.dto.Dtos.DespesaRequest;
import br.com.republica.dto.Dtos.DespesaResponse;
import br.com.republica.dto.Dtos.MeuResumoResponse;
import br.com.republica.dto.Dtos.MinhasDividasResponse;
import br.com.republica.dto.Dtos.MoradorRequest;
import br.com.republica.dto.Dtos.MoradorResponse;
import br.com.republica.exception.RecursoNaoEncontradoException;
import br.com.republica.exception.RegraDeNegocioException;
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

/** Duas irmãs que pagam juntas: dividem por 4 pessoas, mas a cobrança é uma só, para a responsável. */
@SpringBootTest
@Transactional
class AgrupamentoDeMoradoresTest {

    @Autowired CasaService casaService;
    @Autowired DespesaService despesaService;
    @Autowired PainelService painelService;

    Long casaId;
    MoradorResponse nathan, caio, bia, duda;

    @BeforeEach
    void cenario() {
        casaId = casaService.criar(new CasaRequest("Casa da Vila")).id();
        nathan = casaService.adicionarMorador(casaId, new MoradorRequest("Nathan"));
        caio = casaService.adicionarMorador(casaId, new MoradorRequest("Caio"));
        bia = casaService.adicionarMorador(casaId, new MoradorRequest("Bia"));
        duda = casaService.adicionarMorador(casaId, new MoradorRequest("Duda"));
        casaService.definirResponsavel(casaId, bia.id(), caio.id());
    }

    DespesaResponse despesa(MoradorResponse pagador, String valor, boolean separar) {
        return despesaService.registrar(casaId, new DespesaRequest("Mercado", new BigDecimal(valor),
                LocalDate.of(2026, 10, 10),
                List.of(nathan.id(), caio.id(), bia.id(), duda.id()), separar), pagador.id());
    }

    @Test
    void divideEntreQuatroMasAsIrmasViramUmaCobrancaDeMetade() {
        DespesaResponse d = despesa(nathan, "100.00", false);

        assertThat(d.divisoes()).hasSize(3); // Nathan (quitada), Caio+Bia, Duda
        assertThat(d.divisoes()).filteredOn(v -> v.devedor().id().equals(caio.id()))
                .singleElement().satisfies(v -> assertThat(v.valor()).isEqualByComparingTo("50.00"));
        assertThat(d.divisoes()).filteredOn(v -> v.devedor().id().equals(duda.id()))
                .singleElement().satisfies(v -> assertThat(v.valor()).isEqualByComparingTo("25.00"));
    }

    @Test
    void asDuasIrmasVeemADividaDoGrupo() {
        despesa(nathan, "100.00", false);

        for (MoradorResponse irma : List.of(caio, bia)) {
            MeuResumoResponse resumo = painelService.meuResumo(casaId, irma.id());
            assertThat(resumo.deve()).isEqualByComparingTo("50.00");
            MinhasDividasResponse dividas = painelService.minhasDividas(casaId, irma.id());
            assertThat(dividas.euDevo()).hasSize(1);
            assertThat(despesaService.listar(casaId, null, irma.id())).hasSize(1);
        }
    }

    @Test
    void cobrarSeparadoDesfazAJuncaoSoNaquelaDespesa() {
        DespesaResponse d = despesa(nathan, "100.00", true);

        assertThat(d.divisoes()).hasSize(4);
        assertThat(d.divisoes()).allSatisfy(v -> assertThat(v.valor()).isEqualByComparingTo("25.00"));
    }

    @Test
    void quandoAIrmaPagaASuaParteEDaIrmaNaoViraDivida() {
        DespesaResponse d = despesa(bia, "100.00", false);

        // Caio é do mesmo grupo de quem pagou: só Nathan e Duda devem
        assertThat(d.divisoes()).filteredOn(v -> !v.parteDoPagador()).hasSize(2);
        assertThat(painelService.meuResumo(casaId, caio.id()).aReceber()).isEqualByComparingTo("50.00");
        assertThat(painelService.meuResumo(casaId, caio.id()).deve()).isEqualByComparingTo("0");
    }

    @Test
    void naoEnxergaQuemNaoEDoGrupoNemParticipa() {
        despesaService.registrar(casaId, new DespesaRequest("Pizza", new BigDecimal("60.00"),
                LocalDate.of(2026, 10, 10), List.of(nathan.id(), caio.id())), nathan.id());

        assertThat(despesaService.listar(casaId, null, bia.id())).hasSize(1); // irmã da Caio vê
        assertThat(despesaService.listar(casaId, null, duda.id())).isEmpty();
    }

    @Test
    void regrasDoAgrupamento() {
        assertThatThrownBy(() -> casaService.definirResponsavel(casaId, caio.id(), nathan.id()))
                .isInstanceOf(RegraDeNegocioException.class); // Caio já é responsável por Bia
        assertThatThrownBy(() -> casaService.definirResponsavel(casaId, nathan.id(), bia.id()))
                .isInstanceOf(RegraDeNegocioException.class); // Bia já paga junto com Caio
        assertThatThrownBy(() -> casaService.definirResponsavel(casaId, nathan.id(), nathan.id()))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> casaService.desativarMorador(casaId, caio.id()))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> casaService.definirResponsavel(casaId, 99999L, caio.id()))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        casaService.definirResponsavel(casaId, bia.id(), null);
        assertThat(despesa(nathan, "100.00", false).divisoes()).hasSize(4);
    }
}
