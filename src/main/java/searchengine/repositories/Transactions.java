package searchengine.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;


@RequiredArgsConstructor
public class Transactions {

    private final SitesRepository sitesRepository;
    private final PagesRepository pagesRepository;
    private final IndexesRepository indexesRepository;
    private final LemmasRepository lemmasRepository;

    @Transactional
    public Page findByPathLink(String pathLink) {
        return pagesRepository.findByPathLink(pathLink);
    }

    @Transactional
    public Iterable<Page> findAllByPathLink(String pathLink) {
        return pagesRepository.findAllByPathLink(pathLink);
    }

    @Transactional
    public Page findByPathLinkAndSite(String pathLink, Site site) {
        return pagesRepository.findByPathLinkAndSite(pathLink, site);
    }

    @Transactional
    public Site findByUrl(String url) {
        return sitesRepository.findByUrl(url);
    }

    @Transactional
    public Lemma findByLemma(String lemma) {
        return lemmasRepository.findByLemma(lemma);
    }

    /*@Transactional
    public Lemma findLastLemma() {
        return lemmasRepository.findLastLemma();
    }*/

    @Transactional
    public Iterable<Index> findAllByPageId(int pageId) {
        return indexesRepository.findAllByPageId(pageId);
    }

}
