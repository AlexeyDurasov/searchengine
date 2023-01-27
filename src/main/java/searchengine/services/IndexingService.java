package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;

public interface IndexingService {
    IndexingResponse getStartIndexing() throws InterruptedException;

    IndexingResponse getStopIndexing();
}
