package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.*;
import searchengine.repositories.IndexesRepository;
import searchengine.repositories.LemmasRepository;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SitesRepository sitesRepository;
    private final PagesRepository pagesRepository;
    private final IndexesRepository indexesRepository;
    private final LemmasRepository lemmasRepository;

    @Override
    public SearchResponse getSearch(String query, String site, int offset, int limit) {
        SearchResponse searchResponse = new SearchResponse();
        if (query.equals("")) {
            searchResponse.setResult(false);
            searchResponse.setError("Задан пустой поисковый запрос.");
            return searchResponse;
        }
        Site currentSite = sitesRepository.findByUrl(site);
        if (currentSite == null || !currentSite.getStatus().equals(Status.INDEXED)) {
            searchResponse.setResult(false);
            searchResponse.setError("Такого сайта " + site + " нет в базе или он не проиндексирован.");
            return searchResponse;
        }
        LemmaFinder creatingLemmas;
        try {
            creatingLemmas = LemmaFinder.getInstance();
        } catch (Exception ex) {
            searchResponse.setResult(false);
            searchResponse.setError(ex.toString());
            return searchResponse;
        }
        Map<String, Integer> mapLemmas = new HashMap<>(creatingLemmas.collectLemmas(query));
        Set<String> setLemmas = new HashSet<>(mapLemmas.keySet());
        return search(setLemmas);
    }

    private SearchResponse search(Set<String> setLemmas) {
        Map<String, Integer> unsortedMap = new HashMap<>();
        for (String newLemma : setLemmas) {
            Lemma lemma = lemmasRepository.findByLemma(newLemma);
            if (lemma != null) {
                unsortedMap.put(lemma.getLemma(), lemma.getFrequency());
            }
        }
        Map<String, Integer> sortedMap = unsortedMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> { throw new AssertionError(); }, LinkedHashMap::new
                ));
        Set<String> sortedSet = new LinkedHashSet<>(sortedMap.keySet());
        List<Page> pagesList = new ArrayList<>();
        int first = 1;
        for (String newLemma : sortedSet) {
            Lemma lemma = lemmasRepository.findByLemma(newLemma);
            Iterable<Index> indexIterable = indexesRepository.findAllByLemmaId(lemma.getId());
            if (first == 1) {
                for (Index index : indexIterable) {
                    pagesList.add(index.getPage());
                }
                ++first;
            }
            for (Index index : indexIterable) {
                pagesList.remove(index.getPage());
            }
        }
        SearchResponse searchResponse = new SearchResponse();
        searchResponse.setResult(true);
        searchResponse.setCount(pagesList.size());
        List<SearchData> searchDataList = new ArrayList<>();
        for (Page page : pagesList) {
            SearchData searchData = new SearchData();
            searchData.setSite(page.getSite().getUrl());
            searchData.setSiteName(page.getSite().getName());
            searchData.setUri(page.getPathLink());
            /*searchData.setTitle();
            searchData.setSnippet();
            searchData.setRelevance();*/
            searchDataList.add(searchData);
        }
        searchResponse.setSearchData(searchDataList);
        return searchResponse;
    }
}
