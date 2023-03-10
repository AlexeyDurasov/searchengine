package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

@Repository
public interface PagesRepository extends CrudRepository<Page, Integer> {

    Page findByPathLinkAndSite(String pathLink, Site site);
}
