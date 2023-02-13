package searchengine.services;

import searchengine.config.SiteConfig;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.Site;

import java.io.IOException;

public interface IndexingService {
    IndexingResponse getStartIndexing() throws InterruptedException, IOException;

    IndexingResponse getStopIndexing();

    IndexingResponse getIndexingPage(String url) throws IOException;

    Site creatingSite(SiteConfig siteConfig, int index);
}
