package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;

@Repository
public interface LemmasRepository extends CrudRepository<Lemma, Integer> {

    Lemma findByLemma(String lemma);
}
