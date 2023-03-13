package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
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

    private final SitesList sites;
    private final SitesRepository sitesRepository;
    private final PagesRepository pagesRepository;
    private final IndexesRepository indexesRepository;
    private final LemmasRepository lemmasRepository;
    private LemmaFinder creatingLemmas;
    private Set<String> querySet;
    private String site;
    private String query;
    private int offset;
    private int limit;

    @Override
    public SearchResponse getSearch(String querySearch, String siteSearch, int offsetSearch, int limitSearch) {
        site = siteSearch;
        query = querySearch;
        offset = offsetSearch;
        limit = limitSearch;
        SearchResponse searchResponse = new SearchResponse();
        if (site != null) {
            Site currentSite = sitesRepository.findByUrl(site);
            if (currentSite == null || !currentSite.getStatus().equals(Status.INDEXED)) {
                searchResponse.setResult(false);
                searchResponse.setError("Сайт " + site + " не проиндексирован или его нет в базе.");
                return searchResponse;
            }
        } else {
            Iterable<Site> siteIterable = sitesRepository.findAll();
            int siteIterableSize = 0;
            for (Site siteRepo : siteIterable) {
                if (!siteRepo.getStatus().equals(Status.INDEXED)) {
                    searchResponse.setResult(false);
                    searchResponse.setError("Сайт " + siteRepo.getUrl() + " не проиндексирован.");
                    return searchResponse;
                }
                ++siteIterableSize;
            }
            if (siteIterableSize != sites.getSites().size()) {
                searchResponse.setResult(false);
                searchResponse.setError("Количество сайтов в базе не соответствует списку из конфигурации.");
                return searchResponse;
            }
        }
        try {
            creatingLemmas = LemmaFinder.getInstance();
        } catch (Exception ex) {
            searchResponse.setResult(false);
            searchResponse.setError(ex.toString());
            return searchResponse;
        }
        Map<String, Integer> queryLemmas = new HashMap<>(creatingLemmas.collectLemmas(querySearch));
        if (queryLemmas.size() == 0) {
            searchResponse.setResult(false);
            searchResponse.setError("Задан пустой/неверный поисковый запрос. Допустимы только русские слова/буквы.");
            return searchResponse;
        }
        querySet = new HashSet<>(queryLemmas.keySet());
        List<Lemma> allLemmas = searchLemmasAndSort();
        if (allLemmas.size() == 0) {
            searchResponse.setResult(true);
            searchResponse.setSearchData(new ArrayList<>());
            return searchResponse;
        }
        return getResponse(searchPagesAndSort(allLemmas));
    }

    private List<Lemma> searchLemmasAndSort() {
        Set<Lemma> checkUnique = new HashSet<>();
        List<Lemma> allLemmas = new ArrayList<>();
        for (String newLemma : querySet) {
            Iterable<Lemma> lemmaIterable = lemmasRepository.findAllByLemma(newLemma);
            for (Lemma lemma : lemmaIterable) {
                if (site != null && lemma.getSite().getId() == sitesRepository.findByUrl(site).getId()) {
                    allLemmas.add(lemma);
                    checkUnique.add(lemma);
                    break;
                } else if (site == null) {
                    allLemmas.add(lemma);
                    checkUnique.add(lemma);
                }
            }
        }
        if (querySet.size() != checkUnique.size())
            return new ArrayList<>();
        allLemmas.sort(Comparator.comparing(Lemma::getFrequency));
        return allLemmas;
    }

    private Map<String, Float> searchPagesAndSort(List<Lemma> sortLemmas) {
        Map<String, Float> pagesRank = new HashMap<>();
        boolean addPage = true;
        Lemma first = sortLemmas.get(0);
        for (Lemma lemma : sortLemmas) {
            if (first.getFrequency() == lemma.getFrequency() && first.getLemma().equals(lemma.getLemma()))
                addPage = true;
            Iterable<Index> indexIterable = indexesRepository.findAllByLemmaId(lemma.getId());
            for (Index index : indexIterable) {
                Page page = index.getPage();
                String link = page.getPathLink();
                if (link.equals("/"))
                    link = page.getSite().getUrl();
                else
                    link = page.getSite().getUrl() + link.substring(1);
                addOrDelPages(addPage, pagesRank, link, index, page);
            }
            addPage = false;
        }
        return pagesRank.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> {
                            throw new AssertionError();
                        }, LinkedHashMap::new));
    }

    private void addOrDelPages(boolean addPage, Map<String, Float> pagesRank, String link, Index index, Page page) {
        if (addPage) {
            if (!pagesRank.containsKey(link))
                pagesRank.put(link, index.getRank());
            else
                pagesRank.put(link, index.getRank() + pagesRank.get(link));
        } else {
            boolean skipFirstLemma = true;
            Set<String> lemmasSetOnPage = new HashSet<>();
            for (Index eachIndex : page.getIndexes())
                lemmasSetOnPage.add(lemmasRepository.findById(eachIndex.getLemmaId()).get().getLemma());
            for (String eachLemma : querySet) {
                if (skipFirstLemma)
                    skipFirstLemma = false;
                else if (!lemmasSetOnPage.contains(eachLemma))
                    pagesRank.remove(link);
            }
        }
    }

    private SearchResponse getResponse(Map<String, Float> pagesRank) {
        Set<String> pagesSet = new LinkedHashSet<>(pagesRank.keySet());

        SearchResponse searchResponse = new SearchResponse();
        searchResponse.setResult(true);
        searchResponse.setCount(pagesRank.size());

        Float findMaxAbsolutRelevance = 0F;
        List<SearchData> searchDataList = new ArrayList<>();

        for (String link : pagesSet) {
            findMaxAbsolutRelevance = pagesRank.get(link);
            int separationIndex = 0;
            for (int i = 0; i < link.length(); i++) {
                if (link.charAt(i) == '/')
                    ++separationIndex;
                if (separationIndex == 3) {
                    separationIndex = i;
                    break;
                }
            }
            String currentSite = link.substring(0, separationIndex);
            String currentPage = link.substring(separationIndex);
            Site siteRepo = sitesRepository.findByUrl(currentSite + "/");
            Page page = pagesRepository.findByPathLinkAndSite(currentPage, siteRepo);
            SearchData searchData = new SearchData();
            searchData.setSite(currentSite);
            searchData.setSiteName(siteRepo.getName());
            searchData.setUri(currentPage);
            Document doc = Jsoup.parse(page.getContent());
            searchData.setTitle(doc.title());
            searchData.setSnippet(snippet(page.getContent()));
            searchData.setRelevance(findMaxAbsolutRelevance);
            searchDataList.add(searchData);
        }
        for (SearchData searchData : searchDataList)
            searchData.setRelevance(searchData.getRelevance() / findMaxAbsolutRelevance);
        Collections.reverse(searchDataList);
        searchResponse.setSearchData(subList(searchDataList, offset, limit));
        return searchResponse;
    }

    private String snippet(String inText) {
        String[] text = creatingLemmas.arrayContainsRussianWords(inText);
        StringBuilder snippet = new StringBuilder();
        List<String> tList = new ArrayList<>(Arrays.asList(text));
        List<Integer> foundsWordsIndexes = new ArrayList<>();
        tList = createListFromText(tList, foundsWordsIndexes);
        for (String tWord : tList)
            snippet.append(tWord).append(" ");
        return snippet.toString();
    }

    private List<String> createListFromText(List<String> tList, List<Integer> foundsWordsIndexes) {
        for (String qWord : creatingLemmas.arrayContainsRussianWords(query)) {

            int indexOf = tList.indexOf(qWord);
            tList.set(indexOf, "<b>" + qWord + "</b>");
            foundsWordsIndexes.add(indexOf);

            if (tList.size() <= 30)
                continue;

            int startSL = indexOf < 10 ? indexOf : indexOf - 10;
            int endSL = startSL;

            if (startSL + 30 <= tList.size())
                endSL = startSL + 30;
            else if (startSL + 20 <= tList.size())
                endSL = startSL + 20;
            else if (startSL + 10 <= tList.size())
                endSL = startSL + 10;
            tList = tList.subList(startSL, endSL);
        }
        return tList;
    }

    private List<SearchData> subList(List<SearchData> searchData, int offset, int limit) {
        int toIndex = offset + limit;
        if (toIndex > searchData.size()) {
            toIndex = searchData.size();
        }
        if (offset > toIndex) {
            return List.of();
        }
        return searchData.subList(offset, toIndex);
    }
}
