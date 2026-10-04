package br.com.republica.repository;

import br.com.republica.model.Divisao;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DivisaoRepository extends JpaRepository<Divisao, Long> {

    /** Partes ainda não quitadas de uma casa (pendentes ou cobradas), sem contar a parte do próprio pagador. */
    @Query("""
            select d from Divisao d
              join fetch d.devedor
              join fetch d.despesa e
              join fetch e.pagador
            where e.casa.id = :casaId
              and d.status <> br.com.republica.model.StatusDivisao.PAGA
              and d.devedor.id <> e.pagador.id
            """)
    List<Divisao> findEmAbertoPorCasa(@Param("casaId") Long casaId);

    /**
     * Só as partes em aberto de que o morador participa: ou ele deve (devedor) ou ele tem a receber (pagador).
     * É daqui que sai tudo o que a "Minha visão" mostra, então um morador nunca enxerga dívida alheia.
     */
    @Query("""
            select d from Divisao d
              join fetch d.devedor
              join fetch d.despesa e
              join fetch e.pagador
            where e.casa.id = :casaId
              and d.status <> br.com.republica.model.StatusDivisao.PAGA
              and d.devedor.id <> e.pagador.id
              and (d.devedor.id = :moradorId or e.pagador.id = :moradorId)
            """)
    List<Divisao> findEmAbertoDoMorador(@Param("casaId") Long casaId, @Param("moradorId") Long moradorId);

    @EntityGraph(attributePaths = {"devedor", "despesa", "despesa.pagador"})
    Optional<Divisao> findWithDetalhesById(Long id);
}
