package br.com.republica.repository;

import br.com.republica.model.Morador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MoradorRepository extends JpaRepository<Morador, Long> {

    List<Morador> findByCasaIdOrderByNome(Long casaId);

    Optional<Morador> findByIdAndCasaId(Long id, Long casaId);

    List<Morador> findByIdInAndCasaId(List<Long> ids, Long casaId);

    boolean existsByCasaIdAndNomeIgnoreCaseAndAtivoTrue(Long casaId, String nome);
}
