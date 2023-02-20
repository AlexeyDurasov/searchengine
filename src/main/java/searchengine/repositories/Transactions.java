package searchengine.repositories;

import lombok.RequiredArgsConstructor;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;


@RequiredArgsConstructor
public class Transactions {

    /*private final SitesRepository sitesRepository;
    private final PagesRepository pagesRepository;
    private final LemmasRepository lemmasRepository;

    public Page findByPathLink(String pathLink) {
        return pagesRepository.findByPathLink(pathLink);
    }

    public Page findByPathLinkAndSite(String pathLink, Site site) {
        return pagesRepository.findByPathLinkAndSite(pathLink, site);
    }

    public Site findByUrl(String url) {
        return sitesRepository.findByUrl(url);
    }

    public Lemma findByLemma(String lemma) {
        return lemmasRepository.findByLemma(lemma);
    }*/
}
