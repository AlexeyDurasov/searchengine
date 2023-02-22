package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
        if (site != null) {
            Site currentSite = sitesRepository.findByUrl(site);
            if (currentSite == null || !currentSite.getStatus().equals(Status.INDEXED)) {
                searchResponse.setResult(false);
                searchResponse.setError("Сайт " + site + " не проиндексирован или его нет в базе.");
                return searchResponse;
            }
        }
        LemmaFinder creatingLemmas;
        Map<String, Integer> lemmas;
        try {
            creatingLemmas = LemmaFinder.getInstance();
            lemmas = new HashMap<>(creatingLemmas.collectLemmas(query));
        } catch (Exception ex) {
            searchResponse.setResult(false);
            searchResponse.setError(ex.toString());
            return searchResponse;
        }
        if (lemmas.size() == 0) {
            searchResponse.setResult(false);
            searchResponse.setError("Задан пустой/неверный поисковый запрос. Допустимы только русские слова/буквы.");
            return searchResponse;
        }
        Set<String> lemmasSet = new HashSet<>(lemmas.keySet());
        Map<String, Integer> lemmasFrequency = searchLemmas(lemmasSet, site);
        Map<String, Float> pagesRank = searchPages(lemmasFrequency, site);
        return getResponse(pagesRank);
    }

    private Map<String, Integer> searchLemmas(Set<String> lemmasSet, String site) {
        Map<String, Integer> lemmasFrequency = new HashMap<>();
        for (String newLemma : lemmasSet) {
            Iterable<Lemma> lemmaIterable = lemmasRepository.findAllByLemma(newLemma);
            Lemma lemma = null;
            for (Lemma eachLemma: lemmaIterable) {
                if (site != null && eachLemma.getSite().equals(sitesRepository.findByUrl(site))) {
                    lemma = eachLemma;
                    break;
                } else if (site == null) {
                    if (lemma == null) {
                        lemma = eachLemma;
                    } else if (eachLemma.getFrequency() < lemma.getFrequency()) {
                        lemma = eachLemma;
                    }
                }
            }
            if (lemma != null) {
                lemmasFrequency.put(lemma.getLemma(), lemma.getFrequency());
            }
        }
        return lemmasFrequency.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> { throw new AssertionError(); }, LinkedHashMap::new
                ));
    }

    private Map<String, Float> searchPages(Map<String, Integer> lemmasFrequency, String site) {
        Set<String> lemmasSet = new LinkedHashSet<>(lemmasFrequency.keySet());
        Map<String, Float> pagesRank = new HashMap<>();
        boolean first = true;
        for (String newLemma : lemmasSet) {
            Iterable<Lemma> lemmaIterable = lemmasRepository.findAllByLemma(newLemma);
            for (Lemma lemma : lemmaIterable) {
                if (site != null && !lemma.getSite().equals(sitesRepository.findByUrl(site))) {
                    continue;
                }
                Iterable<Index> indexIterable = indexesRepository.findAllByLemmaId(lemma.getId());
                for (Index index : indexIterable) {
                    Page page = index.getPage();
                    String link = page.getPathLink();
                    if (link.equals("/")) {
                        link = page.getSite().getUrl();
                    }
                    if (first) {
                        if (!pagesRank.containsKey(link)) {
                            pagesRank.put(link, index.getRank());
                        } else {
                            pagesRank.put(link, index.getRank() + pagesRank.get(link));
                        }
                    } else {
                        pagesRank.remove(link);
                    }
                }
                if (site != null) {
                    break;
                }
            }
            first = false;
        }
        return pagesRank.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> { throw new AssertionError(); }, LinkedHashMap::new
                ));
    }

    private SearchResponse getResponse(Map<String, Float> pagesRank) {
        Iterable<Site> siteIterable = sitesRepository.findAll();
        Set<String> pagesSet = new LinkedHashSet<>(pagesRank.keySet());

        SearchResponse searchResponse = new SearchResponse();
        searchResponse.setResult(true);
        searchResponse.setCount(pagesRank.size());

        Float findMaxAbsolutRelevance = 0F;
        List<SearchData> searchDataList = new ArrayList<>();
        for (String pathLink : pagesSet) {
            findMaxAbsolutRelevance = pagesRank.get(pathLink);
            Page page = null;
            for (Site siteRepo : siteIterable) {
                if (siteRepo.getUrl().equals(pathLink)) {
                    pathLink = pathLink.substring(siteRepo.getUrl().length()-1);
                    page = pagesRepository.findByPathLinkAndSite(pathLink, siteRepo);
                }
            }
            if (page == null) {
                page = pagesRepository.findByPathLink(pathLink);
            }
            SearchData searchData = new SearchData();
            searchData.setSite(page.getSite().getUrl());
            searchData.setSiteName(page.getSite().getName());
            searchData.setUri(page.getPathLink());

            Document doc = Jsoup.parse(page.getContent());
            searchData.setTitle(doc.title());

            //searchData.setSnippet();

            searchData.setRelevance(findMaxAbsolutRelevance);

            searchDataList.add(searchData);
        }
        for (SearchData searchData : searchDataList) {
            searchData.setRelevance(searchData.getRelevance() / findMaxAbsolutRelevance);
        }
        Collections.reverse(searchDataList);
        searchResponse.setSearchData(searchDataList);
        return searchResponse;
    }


}
