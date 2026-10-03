package br.com.republica.repository;

import br.com.republica.model.Despesa;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DespesaRepository extends JpaRepository<Despesa, Long> {

    @EntityGraph(attributePaths = {"pagador", "divisoes", "divisoes.devedor"})
    List<Despesa> findByCasaIdOrderByDataDescIdDesc(Long casaId);

    @EntityGraph(attributePaths = {"pagador", "divisoes", "divisoes.devedor"})
    List<Despesa> findByCasaIdAndDataBetweenOrderByDataDescIdDesc(Long casaId, LocalDate inicio, LocalDate fim);

    @EntityGraph(attributePaths = {"pagador", "divisoes", "divisoes.devedor"})
    Optional<Despesa> findByIdAndCasaId(Long id, Long casaId);
}
