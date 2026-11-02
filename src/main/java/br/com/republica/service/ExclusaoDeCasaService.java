package br.com.republica.service;

import br.com.republica.exception.RegraDeNegocioException;
import br.com.republica.model.Casa;
import br.com.republica.model.Morador;
import br.com.republica.repository.CasaRepository;
import br.com.republica.repository.ContaFixaRepository;
import br.com.republica.repository.DespesaRepository;
import br.com.republica.repository.DivisaoRepository;
import br.com.republica.repository.MoradorRepository;
import br.com.republica.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Exclui uma casa e tudo o que é dela. É irreversível, por isso exige o nome da casa como confirmação
 * e só acontece quando ninguém tem valores a pagar ou a receber.
 */
@Service
public class ExclusaoDeCasaService {

    private final CasaService casaService;
    private final CasaRepository casas;
    private final MoradorRepository moradores;
    private final UsuarioRepository usuarios;
    private final DespesaRepository despesas;
    private final ContaFixaRepository contasFixas;
    private final DivisaoRepository divisoes;

    public ExclusaoDeCasaService(CasaService casaService, CasaRepository casas, MoradorRepository moradores,
                                 UsuarioRepository usuarios, DespesaRepository despesas,
                                 ContaFixaRepository contasFixas, DivisaoRepository divisoes) {
        this.casaService = casaService;
        this.casas = casas;
        this.moradores = moradores;
        this.usuarios = usuarios;
        this.despesas = despesas;
        this.contasFixas = contasFixas;
        this.divisoes = divisoes;
    }

    @Transactional
    public void excluir(Long casaId, String nomeConfirmado) {
        Casa casa = casaService.buscarEntidade(casaId);
        if (nomeConfirmado == null || !casa.getNome().trim().equalsIgnoreCase(nomeConfirmado.trim())) {
            throw new RegraDeNegocioException("Para excluir, confirme digitando o nome exato da casa.");
        }
        if (!divisoes.findEmAbertoPorCasa(casaId).isEmpty()) {
            throw new RegraDeNegocioException(
                    "Ainda há valores a pagar ou a receber nesta casa. Quite tudo antes de excluir.");
        }

        contasFixas.deleteAll(contasFixas.findByCasaIdOrderByDescricaoAsc(casaId));
        despesas.deleteAll(despesas.findByCasaIdOrderByDataDescIdDesc(casaId)); // leva as divisões junto
        usuarios.deleteAll(usuarios.findByMoradorCasaId(casaId));

        List<Morador> todos = moradores.findByCasaIdOrderByNome(casaId);
        todos.forEach(m -> m.setResponsavel(null)); // desfaz os agrupamentos antes de apagar quem é referenciado
        moradores.flush();
        moradores.deleteAll(todos);
        casas.delete(casa);
    }
}
