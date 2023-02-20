package searchengine.services;

import searchengine.dto.search.SearchResponse;

import java.io.IOException;

public interface SearchService {
    SearchResponse getSearch(String query, String site, int offset, int limit);
}
