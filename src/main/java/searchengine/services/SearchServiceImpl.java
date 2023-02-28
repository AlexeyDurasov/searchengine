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
    private Map<String, String> lemmaWordMap = new HashMap<>();
    private LemmaFinder creatingLemmas;
    private Map<String, Integer> queryLemmas;
    private String site;
    private String queryStr;

    @Override
    public SearchResponse getSearch(String query, String siteSearch, int offset, int limit) {
        site = siteSearch;
        queryStr = query;
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
            if (siteIterableSize == 0) {
                searchResponse.setResult(false);
                searchResponse.setError("В базе нет ни одного сайта.");
                return searchResponse;
            }
            if (siteIterableSize != sites.getSites().size()) {
                searchResponse.setResult(false);
                searchResponse.setError("Количество сайтов в базе не соответствует списку из конфигурации. Запустите индекссацию.");
                return searchResponse;
            }
        }
        try {
            creatingLemmas = LemmaFinder.getInstance();
            queryLemmas = new LinkedHashMap<>(creatingLemmas.collectLemmas(query));
        } catch (Exception ex) {
            searchResponse.setResult(false);
            searchResponse.setError(ex.toString());
            return searchResponse;
        }
        if (queryLemmas.size() == 0) {
            searchResponse.setResult(false);
            searchResponse.setError("Задан пустой/неверный поисковый запрос. Допустимы только русские слова/буквы.");
            return searchResponse;
        }
        queryLemmas = searchLemmasAndSort();
        return getResponse(searchPagesAndSort());
    }

    private Map<String, Integer> searchLemmasAndSort() {
        Set<String> lemmasSet = new HashSet<>(queryLemmas.keySet());
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

    private Map<String, Float> searchPagesAndSort() {
        Set<String> lemmasSet = new LinkedHashSet<>(queryLemmas.keySet());
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
                    } else {
                        link = page.getSite().getUrl() + link.substring(1);
                    }
                    if (first) {
                        if (!pagesRank.containsKey(link)) {
                            pagesRank.put(link, index.getRank());
                        } else {
                            pagesRank.put(link, index.getRank() + pagesRank.get(link));
                        }
                    } else {
                        int firstLemma = 1;
                        Map<String, Integer> pageMap = new HashMap<>(creatingLemmas.collectLemmas(page.getContent()));
                        for (String eachLemma : lemmasSet) {
                            if (firstLemma++ == 1) {
                                continue;
                            }
                            if (!pageMap.containsKey(eachLemma)) {
                                pagesRank.remove(link);
                            }
                        }
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
        for (SearchData searchData : searchDataList) {
            searchData.setRelevance(searchData.getRelevance() / findMaxAbsolutRelevance);
        }
        Collections.reverse(searchDataList);
        searchResponse.setSearchData(searchDataList);
        return searchResponse;
    }

    private String snippet(String inText) {
        String[] text = creatingLemmas.arrayContainsRussianWords(inText);
        StringBuilder snippet = new StringBuilder();
        List<String> tList = new ArrayList<>(Arrays.asList(text));
        List<Integer> foundsWordsIndexes = new ArrayList<>();
        tList = createListFromText(tList, foundsWordsIndexes);
        for (String tWord : tList) {
            snippet.append(tWord).append(" ");
        }
        return snippet.toString();
    }

    private List<String> createListFromText(List<String> tList, List<Integer> foundsWordsIndexes) {
        for (String qWord : queryStr.trim().split("\\s+")) {

            int indexOf = tList.indexOf(qWord);
            tList.set(indexOf, "<b>" + qWord + "</b>");
            foundsWordsIndexes.add(indexOf);

            if (tList.size() <= 30) {
                continue;
            }

            int startSL = indexOf < 10 ? indexOf : indexOf - 10;
            int endSL = startSL;

            if (startSL + 30 <= tList.size()) {
                endSL = startSL + 30;
            } else if (startSL + 20 <= tList.size()) {
                endSL =  startSL + 20;
            } else if (startSL + 10 <= tList.size()) {
                endSL = startSL + 10;
            }

            tList = tList.subList(startSL, endSL);
        }
        return tList;
    }
}
