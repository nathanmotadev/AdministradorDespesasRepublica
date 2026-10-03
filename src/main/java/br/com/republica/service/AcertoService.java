package br.com.republica.service;

import br.com.republica.dto.Dtos.AcertoResponse;
import br.com.republica.dto.Dtos.MoradorResponse;
import br.com.republica.dto.Dtos.ResumoResponse;
import br.com.republica.dto.Dtos.SaldoMoradorResponse;
import br.com.republica.model.Casa;
import br.com.republica.model.Divisao;
import br.com.republica.model.Morador;
import br.com.republica.repository.DivisaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula "quem deve quanto a quem". Dívidas nos dois sentidos entre a mesma dupla
 * são compensadas: se Larissa deve R$ 50 a Nathan e Nathan deve R$ 20 a Larissa,
 * o acerto é Larissa pagar R$ 30 a Nathan.
 */
@Service
public class AcertoService {

    private record Par(Long menorId, Long maiorId) {
    }

    private final CasaService casaService;
    private final DivisaoRepository divisoes;

    public AcertoService(CasaService casaService, DivisaoRepository divisoes) {
        this.casaService = casaService;
        this.divisoes = divisoes;
    }

    @Transactional(readOnly = true)
    public ResumoResponse resumo(Long casaId) {
        Casa casa = casaService.buscarEntidade(casaId);
        List<Divisao> emAberto = divisoes.findEmAbertoPorCasa(casaId);

        Map<Long, Morador> moradoresPorId = indexarMoradores(casa, emAberto);
        List<AcertoResponse> acertos = calcularAcertos(emAberto, moradoresPorId);
        List<SaldoMoradorResponse> saldos = calcularSaldos(casa, acertos);
        return new ResumoResponse(saldos, acertos);
    }

    private Map<Long, Morador> indexarMoradores(Casa casa, List<Divisao> emAberto) {
        Map<Long, Morador> porId = new LinkedHashMap<>();
        casa.getMoradores().forEach(m -> porId.put(m.getId(), m));
        emAberto.forEach(d -> {
            porId.putIfAbsent(d.getDevedor().getId(), d.getDevedor());
            porId.putIfAbsent(d.getDespesa().getPagador().getId(), d.getDespesa().getPagador());
        });
        return porId;
    }

    /** Positivo no mapa = o menor id deve ao maior id; negativo = o contrário. */
    private List<AcertoResponse> calcularAcertos(List<Divisao> emAberto, Map<Long, Morador> moradores) {
        Map<Par, BigDecimal> liquido = new HashMap<>();
        for (Divisao d : emAberto) {
            Long devedor = d.getDevedor().getId();
            Long credor = d.getDespesa().getPagador().getId();
            if (devedor < credor) {
                liquido.merge(new Par(devedor, credor), d.getValor(), BigDecimal::add);
            } else {
                liquido.merge(new Par(credor, devedor), d.getValor().negate(), BigDecimal::add);
            }
        }

        return liquido.entrySet().stream()
                .filter(e -> e.getValue().signum() != 0)
                .map(e -> paraAcerto(e.getKey(), e.getValue(), moradores))
                .sorted(Comparator.comparing((AcertoResponse a) -> a.de().nome(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(a -> a.para().nome(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private AcertoResponse paraAcerto(Par par, BigDecimal valor, Map<Long, Morador> moradores) {
        MoradorResponse menor = MoradorResponse.de(moradores.get(par.menorId()));
        MoradorResponse maior = MoradorResponse.de(moradores.get(par.maiorId()));
        return valor.signum() > 0
                ? new AcertoResponse(menor, maior, valor)
                : new AcertoResponse(maior, menor, valor.negate());
    }

    private List<SaldoMoradorResponse> calcularSaldos(Casa casa, List<AcertoResponse> acertos) {
        return casa.getMoradores().stream()
                .filter(Morador::isAtivo)
                .map(m -> new SaldoMoradorResponse(
                        MoradorResponse.de(m),
                        somar(acertos, a -> a.para().id().equals(m.getId())),
                        somar(acertos, a -> a.de().id().equals(m.getId()))))
                .toList();
    }

    private BigDecimal somar(List<AcertoResponse> acertos, java.util.function.Predicate<AcertoResponse> filtro) {
        return acertos.stream().filter(filtro).map(AcertoResponse::valor)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
