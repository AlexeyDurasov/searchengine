package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;

@Repository
public interface IndexesRepository extends CrudRepository<Index, Integer> {

    Iterable<Index> findAllByPageId(int pageId);
}
