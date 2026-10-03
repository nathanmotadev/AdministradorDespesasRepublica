package br.com.republica.service;

import br.com.republica.dto.Dtos.CasaRequest;
import br.com.republica.dto.Dtos.CasaResponse;
import br.com.republica.dto.Dtos.MoradorRequest;
import br.com.republica.dto.Dtos.MoradorResponse;
import br.com.republica.exception.RecursoNaoEncontradoException;
import br.com.republica.exception.RegraDeNegocioException;
import br.com.republica.model.Casa;
import br.com.republica.model.Morador;
import br.com.republica.repository.CasaRepository;
import br.com.republica.repository.DivisaoRepository;
import br.com.republica.repository.MoradorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CasaService {

    private final CasaRepository casas;
    private final MoradorRepository moradores;
    private final DivisaoRepository divisoes;

    public CasaService(CasaRepository casas, MoradorRepository moradores, DivisaoRepository divisoes) {
        this.casas = casas;
        this.moradores = moradores;
        this.divisoes = divisoes;
    }

    @Transactional
    public CasaResponse criar(CasaRequest request) {
        return CasaResponse.de(casas.save(new Casa(request.nome().trim())));
    }

    @Transactional(readOnly = true)
    public List<CasaResponse> listar() {
        return casas.findAll().stream().map(CasaResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public CasaResponse buscar(Long casaId) {
        return CasaResponse.de(buscarEntidade(casaId));
    }

    @Transactional
    public MoradorResponse adicionarMorador(Long casaId, MoradorRequest request) {
        Casa casa = buscarEntidade(casaId);
        String nome = request.nome().trim();
        if (moradores.existsByCasaIdAndNomeIgnoreCaseAndAtivoTrue(casaId, nome)) {
            throw new RegraDeNegocioException("Já existe um morador chamado " + nome + " nesta casa.");
        }
        return MoradorResponse.de(moradores.save(new Morador(nome, casa)));
    }

    /** O morador sai da casa, mas continua no histórico. Só é possível se não houver dívidas em aberto. */
    @Transactional
    public void desativarMorador(Long casaId, Long moradorId) {
        Morador morador = moradores.findByIdAndCasaId(moradorId, casaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Morador", moradorId));

        boolean temPendencias = divisoes.findEmAbertoPorCasa(casaId).stream()
                .anyMatch(d -> d.getDevedor().getId().equals(moradorId)
                        || d.getDespesa().getPagador().getId().equals(moradorId));
        if (temPendencias) {
            throw new RegraDeNegocioException(
                    morador.getNome() + " ainda tem valores a pagar ou a receber. Quite tudo antes de remover.");
        }
        morador.setAtivo(false);
    }

    Casa buscarEntidade(Long casaId) {
        return casas.findById(casaId).orElseThrow(() -> new RecursoNaoEncontradoException("Casa", casaId));
    }
}
