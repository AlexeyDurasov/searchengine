package searchengine.services;

import searchengine.config.Connect;
import searchengine.config.SiteConfig;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.IndexesRepository;
import searchengine.repositories.LemmasRepository;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

public interface IndexingService {
    IndexingResponse getStartIndexing();

    IndexingResponse getStopIndexing();

    IndexingResponse getIndexingPage(String url);

    Site creatingSite(SiteConfig siteConfig, Status status);

    boolean isStopFlag();

    Connect getConnect();

    SitesRepository getSitesRepository();

    PagesRepository getPagesRepository();

    IndexesRepository getIndexesRepository();

    LemmasRepository getLemmasRepository();
}
