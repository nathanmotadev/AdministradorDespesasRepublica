package br.com.republica.service;

import br.com.republica.dto.Dtos.CasaRequest;
import br.com.republica.dto.Dtos.CasaResponse;
import br.com.republica.dto.Dtos.ConviteResponse;
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

import java.security.SecureRandom;

@Service
public class CasaService {

    /** Sem 0/O e 1/I, para o código ser fácil de ditar e digitar. */
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TAMANHO_DO_CODIGO = 6;

    private final SecureRandom aleatorio = new SecureRandom();
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
        return CasaResponse.de(criarEntidade(request.nome()));
    }

    @Transactional(readOnly = true)
    public CasaResponse buscar(Long casaId) {
        Casa casa = buscarEntidade(casaId);
        return CasaResponse.de(casa, moradores.findByCasaIdOrderByNome(casaId));
    }

    @Transactional
    public CasaResponse renomear(Long casaId, CasaRequest request) {
        Casa casa = buscarEntidade(casaId);
        casa.setNome(request.nome().trim());
        return CasaResponse.de(casa, moradores.findByCasaIdOrderByNome(casaId));
    }

    /** Devolve o código de convite da casa (gera um se a casa ainda não tiver). */
    @Transactional
    public ConviteResponse codigoDeConvite(Long casaId) {
        Casa casa = buscarEntidade(casaId);
        if (casa.getCodigoConvite() == null) {
            casa.setCodigoConvite(gerarCodigo());
        }
        return new ConviteResponse(casa.getCodigoConvite());
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
        boolean temAgregados = !moradores.findByCasaIdAndResponsavelId(casaId, moradorId).isEmpty();
        if (temAgregados) {
            throw new RegraDeNegocioException(
                    morador.getNome() + " é responsável por quem paga junto com ele. Desfaça o agrupamento antes de remover.");
        }
        morador.setAtivo(false);
        morador.setResponsavel(null);
    }

    /**
     * Define que um morador paga junto com outro (o responsável), ou desfaz o agrupamento com {@code null}.
     * Só um nível: o responsável não pode ser agregado de ninguém, e quem tem agregados não vira agregado.
     */
    @Transactional
    public MoradorResponse definirResponsavel(Long casaId, Long moradorId, Long responsavelId) {
        Morador morador = moradores.findByIdAndCasaId(moradorId, casaId)
                .filter(Morador::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Morador", moradorId));
        if (responsavelId == null) {
            morador.setResponsavel(null);
            return MoradorResponse.de(morador);
        }
        if (responsavelId.equals(moradorId)) {
            throw new RegraDeNegocioException("Um morador não pode ser responsável por si mesmo.");
        }
        Morador responsavel = moradores.findByIdAndCasaId(responsavelId, casaId)
                .filter(Morador::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Morador", responsavelId));
        if (responsavel.getResponsavel() != null) {
            throw new RegraDeNegocioException(responsavel.getNome() + " já paga junto com outra pessoa.");
        }
        if (!moradores.findByCasaIdAndResponsavelId(casaId, moradorId).isEmpty()) {
            throw new RegraDeNegocioException(morador.getNome() + " já é responsável por alguém.");
        }
        morador.setResponsavel(responsavel);
        return MoradorResponse.de(morador);
    }

    /** Cria a casa já com o código de convite. Usado também pelo cadastro. */
    Casa criarEntidade(String nome) {
        return casas.save(new Casa(nome.trim(), gerarCodigo()));
    }

    Casa buscarEntidade(Long casaId) {
        return casas.findById(casaId).orElseThrow(() -> new RecursoNaoEncontradoException("Casa", casaId));
    }

    private String gerarCodigo() {
        String codigo;
        do {
            StringBuilder texto = new StringBuilder("CASA-");
            for (int i = 0; i < TAMANHO_DO_CODIGO; i++) {
                texto.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
            }
            codigo = texto.toString();
        } while (casas.existsByCodigoConvite(codigo));
        return codigo;
    }
}
