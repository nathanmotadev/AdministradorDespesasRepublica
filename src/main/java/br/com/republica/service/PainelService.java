package br.com.republica.service;

import br.com.republica.dto.Dtos.DividaResponse;
import br.com.republica.dto.Dtos.ItemDeAcertoResponse;
import br.com.republica.dto.Dtos.MeuAcertoResponse;
import br.com.republica.dto.Dtos.MeuResumoResponse;
import br.com.republica.dto.Dtos.MinhasDividasResponse;
import br.com.republica.dto.Dtos.MoradorResponse;
import br.com.republica.model.Divisao;
import br.com.republica.model.Morador;
import br.com.republica.repository.DivisaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A "Minha visão": tudo o que um morador enxerga sobre dinheiro. Todas as consultas partem de
 * {@link DivisaoRepository#findEmAbertoDoMorador}, que só devolve dívidas em que ele participa.
 */
@Service
public class PainelService {

    private final DivisaoRepository divisoes;

    public PainelService(DivisaoRepository divisoes) {
        this.divisoes = divisoes;
    }

    /** Quanto o morador deve e quanto tem a receber, por pessoa, já compensando os dois sentidos. */
    @Transactional(readOnly = true)
    public MeuResumoResponse meuResumo(Long casaId, Long moradorId) {
        Map<Long, Morador> pessoas = new LinkedHashMap<>();
        Map<Long, List<ItemDeAcertoResponse>> itensPorPessoa = new LinkedHashMap<>();

        for (Divisao d : divisoes.findEmAbertoDoMorador(casaId, moradorId)) {
            boolean euDevo = d.getDevedor().getId().equals(moradorId);
            Morador outro = euDevo ? d.getDespesa().getPagador() : d.getDevedor();
            BigDecimal valor = euDevo ? d.getValor().negate() : d.getValor();
            pessoas.putIfAbsent(outro.getId(), outro);
            itensPorPessoa.computeIfAbsent(outro.getId(), id -> new ArrayList<>())
                    .add(new ItemDeAcertoResponse(d.getDespesa().getDescricao(), valor));
        }

        List<MeuAcertoResponse> acertos = new ArrayList<>();
        for (Map.Entry<Long, List<ItemDeAcertoResponse>> entrada : itensPorPessoa.entrySet()) {
            BigDecimal total = entrada.getValue().stream()
                    .map(ItemDeAcertoResponse::valor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.signum() == 0) {
                continue;
            }
            acertos.add(new MeuAcertoResponse(MoradorResponse.de(pessoas.get(entrada.getKey())),
                    total.signum() < 0, total.abs(), entrada.getValue()));
        }
        acertos.sort(Comparator.comparing(MeuAcertoResponse::euDevo).reversed()
                .thenComparing(a -> a.pessoa().nome(), String.CASE_INSENSITIVE_ORDER));

        BigDecimal deve = somar(acertos, true);
        BigDecimal aReceber = somar(acertos, false);
        return new MeuResumoResponse(deve, aReceber, acertos);
    }

    /** As partes em aberto, separadas em "eu devo" e "me devem". */
    @Transactional(readOnly = true)
    public MinhasDividasResponse minhasDividas(Long casaId, Long moradorId) {
        List<Divisao> abertas = new ArrayList<>(divisoes.findEmAbertoDoMorador(casaId, moradorId));
        abertas.sort(Comparator.comparing((Divisao d) -> d.getDespesa().getData())
                .thenComparing(Divisao::getId).reversed());

        List<DividaResponse> euDevo = new ArrayList<>();
        List<DividaResponse> meDevem = new ArrayList<>();
        for (Divisao d : abertas) {
            boolean devoEsta = d.getDevedor().getId().equals(moradorId);
            Morador pessoa = devoEsta ? d.getDespesa().getPagador() : d.getDevedor();
            DividaResponse divida = new DividaResponse(d.getId(), d.getDespesa().getId(),
                    d.getDespesa().getDescricao(), d.getDespesa().getData(), MoradorResponse.de(pessoa),
                    d.getValor(), d.getStatus(), d.getVencimento(), d.isAtrasada(LocalDate.now()));
            (devoEsta ? euDevo : meDevem).add(divida);
        }
        return new MinhasDividasResponse(euDevo, meDevem);
    }

    private BigDecimal somar(List<MeuAcertoResponse> acertos, boolean euDevo) {
        return acertos.stream()
                .filter(a -> a.euDevo() == euDevo)
                .map(MeuAcertoResponse::valor)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
