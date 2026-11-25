package br.com.republica.service;

import br.com.republica.dto.Dtos.ItemDoMesResponse;
import br.com.republica.dto.Dtos.ResumoMesResponse;
import br.com.republica.model.Despesa;
import br.com.republica.model.Divisao;
import br.com.republica.repository.DespesaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * O resumo do mês. Parte sempre das despesas em que o morador pagou ou tem parte
 * (as mesmas que {@link DespesaService#listar} mostra), então nenhum número carrega gasto alheio.
 */
@Service
public class ResumoMesService {

    /** Quantas contas aparecem separadas; o resto vira "Outras". */
    static final int MAX_CONTAS = 5;

    private final CasaService casaService;
    private final DespesaRepository despesas;
    private final GrupoService grupos;
    private final Clock relogio;

    public ResumoMesService(CasaService casaService, DespesaRepository despesas, GrupoService grupos, Clock relogio) {
        this.casaService = casaService;
        this.despesas = despesas;
        this.grupos = grupos;
        this.relogio = relogio;
    }

    /** {@code mes} nulo vale o mês atual. */
    @Transactional(readOnly = true)
    public ResumoMesResponse resumir(Long casaId, YearMonth mes, Long solicitanteId) {
        casaService.buscarEntidade(casaId);
        YearMonth alvo = mes != null ? mes : YearMonth.now(relogio);
        Long grupo = grupos.idDoGrupo(solicitanteId);

        List<Despesa> doMes = visiveis(casaId, alvo, grupo);
        List<Despesa> doMesAnterior = visiveis(casaId, alvo.minusMonths(1), grupo);

        BigDecimal minhaParte = doMes.stream()
                .flatMap(d -> d.getDivisoes().stream())
                .filter(div -> div.getDevedor().idDoGrupo().equals(grupo))
                .map(Divisao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal euPaguei = soma(doMes.stream().filter(d -> d.getPagador().idDoGrupo().equals(grupo)).toList());

        return new ResumoMesResponse(alvo.toString(), doMes.size(), soma(doMes), soma(doMesAnterior),
                minhaParte, euPaguei, porConta(doMes), porPagador(doMes));
    }

    private List<Despesa> visiveis(Long casaId, YearMonth mes, Long grupo) {
        return despesas.findByCasaIdAndDataBetweenOrderByDataDescIdDesc(casaId, mes.atDay(1), mes.atEndOfMonth())
                .stream()
                .filter(d -> d.envolve(grupo))
                .toList();
    }

    private static BigDecimal soma(List<Despesa> lista) {
        return lista.stream().map(Despesa::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Agrupa pelo nome da conta ("luz", "Luz " e "LUZ" são a mesma) e junta as menores em "Outras". */
    static List<ItemDoMesResponse> porConta(List<Despesa> lista) {
        Map<String, String> rotulos = new LinkedHashMap<>();
        Map<String, BigDecimal> valores = new LinkedHashMap<>();
        for (Despesa d : lista) {
            String rotulo = d.getDescricao().trim();
            String chave = rotulo.toLowerCase(Locale.ROOT);
            rotulos.putIfAbsent(chave, rotulo);
            valores.merge(chave, d.getValorTotal(), BigDecimal::add);
        }
        List<ItemDoMesResponse> itens = new ArrayList<>();
        valores.forEach((chave, valor) -> itens.add(new ItemDoMesResponse(rotulos.get(chave), valor)));
        itens.sort(Comparator.comparing(ItemDoMesResponse::valor).reversed());
        if (itens.size() <= MAX_CONTAS) {
            return itens;
        }
        List<ItemDoMesResponse> resposta = new ArrayList<>(itens.subList(0, MAX_CONTAS));
        BigDecimal resto = itens.subList(MAX_CONTAS, itens.size()).stream()
                .map(ItemDoMesResponse::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        resposta.add(new ItemDoMesResponse("Outras", resto));
        return resposta;
    }

    private static List<ItemDoMesResponse> porPagador(List<Despesa> lista) {
        Map<Long, String> nomes = new LinkedHashMap<>();
        Map<Long, BigDecimal> valores = new LinkedHashMap<>();
        for (Despesa d : lista) {
            nomes.putIfAbsent(d.getPagador().getId(), d.getPagador().getNome());
            valores.merge(d.getPagador().getId(), d.getValorTotal(), BigDecimal::add);
        }
        List<ItemDoMesResponse> itens = new ArrayList<>();
        valores.forEach((id, valor) -> itens.add(new ItemDoMesResponse(nomes.get(id), valor)));
        itens.sort(Comparator.comparing(ItemDoMesResponse::valor).reversed());
        return itens;
    }
}
