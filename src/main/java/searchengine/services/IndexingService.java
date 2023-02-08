package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;

import java.io.IOException;

public interface IndexingService {
    IndexingResponse getStartIndexing() throws InterruptedException, IOException;

    IndexingResponse getStopIndexing();

    IndexingResponse getIndexingPage(String url) throws IOException;
}
