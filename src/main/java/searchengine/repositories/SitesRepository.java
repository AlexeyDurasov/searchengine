package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Site;

@Repository
public interface SitesRepository extends CrudRepository<Site, Integer> {

    Site findByUrl(String url);
}
