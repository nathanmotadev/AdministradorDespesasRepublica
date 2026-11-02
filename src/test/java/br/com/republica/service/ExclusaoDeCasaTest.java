package br.com.republica.service;

import br.com.republica.dto.Dtos.CasaRequest;
import br.com.republica.dto.Dtos.DespesaRequest;
import br.com.republica.dto.Dtos.DespesaResponse;
import br.com.republica.dto.Dtos.MoradorRequest;
import br.com.republica.dto.Dtos.MoradorResponse;
import br.com.republica.exception.RegraDeNegocioException;
import br.com.republica.repository.CasaRepository;
import br.com.republica.repository.DespesaRepository;
import br.com.republica.repository.MoradorRepository;
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

/** Excluir a casa: confirmação pelo nome, bloqueio com dívidas e remoção de tudo. */
@SpringBootTest
@Transactional
class ExclusaoDeCasaTest {

    @Autowired CasaService casaService;
    @Autowired DespesaService despesaService;
    @Autowired ExclusaoDeCasaService exclusao;
    @Autowired CasaRepository casas;
    @Autowired MoradorRepository moradores;
    @Autowired DespesaRepository despesas;

    Long casaId;
    MoradorResponse nathan, larissa, camilly;
    DespesaResponse despesa;

    @BeforeEach
    void cenario() {
        casaId = casaService.criar(new CasaRequest("Casa da Vila")).id();
        nathan = casaService.adicionarMorador(casaId, new MoradorRequest("Nathan"));
        larissa = casaService.adicionarMorador(casaId, new MoradorRequest("Larissa"));
        camilly = casaService.adicionarMorador(casaId, new MoradorRequest("Camilly"));
        casaService.definirResponsavel(casaId, camilly.id(), larissa.id());
        despesa = despesaService.registrar(casaId, new DespesaRequest("Mercado", new BigDecimal("90.00"),
                LocalDate.of(2026, 10, 10), List.of(nathan.id(), larissa.id(), camilly.id())), nathan.id());
    }

    @Test
    void exigeONomeExatoDaCasa() {
        assertThatThrownBy(() -> exclusao.excluir(casaId, "outra casa"))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThat(casas.findById(casaId)).isPresent();
    }

    @Test
    void bloqueiaEnquantoHouverDividaEmAberto() {
        assertThatThrownBy(() -> exclusao.excluir(casaId, "Casa da Vila"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Quite tudo");
        assertThat(casas.findById(casaId)).isPresent();
    }

    @Test
    void semDividasApagaCasaMoradoresEDespesas() {
        despesa.divisoes().stream().filter(d -> !d.parteDoPagador())
                .forEach(d -> despesaService.registrarPagamento(d.id(), nathan.id()));

        exclusao.excluir(casaId, " casa da vila ");

        assertThat(casas.findById(casaId)).isEmpty();
        assertThat(moradores.findByCasaIdOrderByNome(casaId)).isEmpty();
        assertThat(despesas.findByCasaIdOrderByDataDescIdDesc(casaId)).isEmpty();
    }
}
