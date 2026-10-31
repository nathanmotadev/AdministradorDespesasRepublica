package br.com.republica.service;

import br.com.republica.model.Morador;
import br.com.republica.repository.MoradorRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/** Quem "paga junto": um responsável e os moradores agregados a ele (um nível, sem correntes). */
@Service
public class GrupoService {

    private final MoradorRepository moradores;

    public GrupoService(MoradorRepository moradores) {
        this.moradores = moradores;
    }

    /** Id do grupo do morador: o do responsável, se houver, ou o dele mesmo. */
    public Long idDoGrupo(Long moradorId) {
        return moradores.findById(moradorId).map(Morador::idDoGrupo).orElse(moradorId);
    }

    /** Ids de todos os moradores do grupo (o responsável e os agregados), incluindo o próprio. */
    public Set<Long> idsDoGrupo(Long casaId, Long moradorId) {
        Long raiz = idDoGrupo(moradorId);
        Set<Long> ids = new HashSet<>();
        ids.add(raiz);
        ids.add(moradorId);
        moradores.findByCasaIdAndResponsavelId(casaId, raiz).forEach(m -> ids.add(m.getId()));
        return ids;
    }
}
