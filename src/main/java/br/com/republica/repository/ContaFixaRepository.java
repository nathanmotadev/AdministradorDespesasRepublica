package br.com.republica.repository;

import br.com.republica.model.ContaFixa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContaFixaRepository extends JpaRepository<ContaFixa, Long> {

    @EntityGraph(attributePaths = {"pagador", "participantes"})
    List<ContaFixa> findByCasaIdOrderByDescricaoAsc(Long casaId);

    Optional<ContaFixa> findByIdAndCasaId(Long id, Long casaId);

    /** Consulta barata, sem trava: diz se existe algo para gerar antes de entrar na parte com trava. */
    boolean existsByCasaIdAndAtivaTrueAndProximoVencimentoLessThanEqual(Long casaId, LocalDate data);

    /** Com trava de escrita, para duas pessoas abrindo o app juntas não gerarem a mesma despesa duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ContaFixa> findByCasaIdAndAtivaTrue(Long casaId);
}
