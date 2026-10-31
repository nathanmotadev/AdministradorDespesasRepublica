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
    MoradorResponse nathan, larissa, camilly, matheus;

    @BeforeEach
    void cenario() {
        casaId = casaService.criar(new CasaRequest("Casa da Vila")).id();
        nathan = casaService.adicionarMorador(casaId, new MoradorRequest("Nathan"));
        larissa = casaService.adicionarMorador(casaId, new MoradorRequest("Larissa"));
        camilly = casaService.adicionarMorador(casaId, new MoradorRequest("Camilly"));
        matheus = casaService.adicionarMorador(casaId, new MoradorRequest("Matheus"));
        casaService.definirResponsavel(casaId, camilly.id(), larissa.id());
    }

    DespesaResponse despesa(MoradorResponse pagador, String valor, boolean separar) {
        return despesaService.registrar(casaId, new DespesaRequest("Mercado", new BigDecimal(valor),
                LocalDate.of(2026, 10, 10),
                List.of(nathan.id(), larissa.id(), camilly.id(), matheus.id()), separar), pagador.id());
    }

    @Test
    void divideEntreQuatroMasAsIrmasViramUmaCobrancaDeMetade() {
        DespesaResponse d = despesa(nathan, "100.00", false);

        assertThat(d.divisoes()).hasSize(3); // Nathan (quitada), Larissa+Camilly, Matheus
        assertThat(d.divisoes()).filteredOn(v -> v.devedor().id().equals(larissa.id()))
                .singleElement().satisfies(v -> assertThat(v.valor()).isEqualByComparingTo("50.00"));
        assertThat(d.divisoes()).filteredOn(v -> v.devedor().id().equals(matheus.id()))
                .singleElement().satisfies(v -> assertThat(v.valor()).isEqualByComparingTo("25.00"));
    }

    @Test
    void asDuasIrmasVeemADividaDoGrupo() {
        despesa(nathan, "100.00", false);

        for (MoradorResponse irma : List.of(larissa, camilly)) {
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
        DespesaResponse d = despesa(camilly, "100.00", false);

        // Larissa é do mesmo grupo de quem pagou: só Nathan e Matheus devem
        assertThat(d.divisoes()).filteredOn(v -> !v.parteDoPagador()).hasSize(2);
        assertThat(painelService.meuResumo(casaId, larissa.id()).aReceber()).isEqualByComparingTo("50.00");
        assertThat(painelService.meuResumo(casaId, larissa.id()).deve()).isEqualByComparingTo("0");
    }

    @Test
    void naoEnxergaQuemNaoEDoGrupoNemParticipa() {
        despesaService.registrar(casaId, new DespesaRequest("Pizza", new BigDecimal("60.00"),
                LocalDate.of(2026, 10, 10), List.of(nathan.id(), larissa.id())), nathan.id());

        assertThat(despesaService.listar(casaId, null, camilly.id())).hasSize(1); // irmã da Larissa vê
        assertThat(despesaService.listar(casaId, null, matheus.id())).isEmpty();
    }

    @Test
    void regrasDoAgrupamento() {
        assertThatThrownBy(() -> casaService.definirResponsavel(casaId, larissa.id(), nathan.id()))
                .isInstanceOf(RegraDeNegocioException.class); // Larissa já é responsável por Camilly
        assertThatThrownBy(() -> casaService.definirResponsavel(casaId, nathan.id(), camilly.id()))
                .isInstanceOf(RegraDeNegocioException.class); // Camilly já paga junto com Larissa
        assertThatThrownBy(() -> casaService.definirResponsavel(casaId, nathan.id(), nathan.id()))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> casaService.desativarMorador(casaId, larissa.id()))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> casaService.definirResponsavel(casaId, 99999L, larissa.id()))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        casaService.definirResponsavel(casaId, camilly.id(), null);
        assertThat(despesa(nathan, "100.00", false).divisoes()).hasSize(4);
    }
}
