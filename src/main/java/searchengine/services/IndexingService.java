package searchengine.services;

import searchengine.config.SiteConfig;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.Site;
import searchengine.model.Status;

import java.io.IOException;

public interface IndexingService {
    IndexingResponse getStartIndexing();

    IndexingResponse getStopIndexing();

    IndexingResponse getIndexingPage(String url);

    Site creatingSite(SiteConfig siteConfig, Status status);

    boolean isStopFlag();
}
