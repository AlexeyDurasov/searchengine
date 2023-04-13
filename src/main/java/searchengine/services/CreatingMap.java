package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.Connect;
import searchengine.model.*;
import searchengine.repositories.IndexesRepository;
import searchengine.repositories.LemmasRepository;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveAction;

@Slf4j
public class CreatingMap extends RecursiveAction {

    private final Site mainSite;
    private final String root;
    private final Connect connect;
    private final IndexingService indexingService;
    private final SitesRepository sitesRepository;
    private final PagesRepository pagesRepository;
    private final IndexesRepository indexesRepository;
    private final LemmasRepository lemmasRepository;

    public CreatingMap(Site mainSite, String root, IndexingService indexingService) {
        this.mainSite = mainSite;
        this.root = root;
        this.connect = indexingService.getConnect();
        this.indexingService = indexingService;
        this.sitesRepository = indexingService.getSitesRepository();
        this.pagesRepository = indexingService.getPagesRepository();
        this.indexesRepository = indexingService.getIndexesRepository();
        this.lemmasRepository = indexingService.getLemmasRepository();
    }

    @Override
    protected void compute() {
        if (indexingService.isStopFlag()) {
            return;
        }
        Set<CreatingMap> tasks = new HashSet<>();
        Set<String> pageLinks = parsePage(root);
        for (String link : pageLinks) {
            CreatingMap creatingMap = new CreatingMap(mainSite, link, indexingService);
            tasks.add(creatingMap);
        }
        for (CreatingMap task : tasks) {
            task.fork();
        }
        for (CreatingMap task : tasks) {
            task.join();
        }
    }

    private Set<String> parsePage(String url) {
        Set<String> links = new HashSet<>();
        try {
            if (checkURL(url)) {
                Connection connection = Jsoup.connect(url).userAgent(connect.getUserAgent())
                        .referrer(connect.getReferrer()).maxBodySize(0);
                Document doc = connection.get();
                Elements elements = doc.select("a[href]");
                String content = connection.get().toString();
                int statusCode = connection.execute().statusCode();
                if (addNewURL(url, statusCode, content)) {
                    for (Element element : elements) {
                        String link = element.absUrl("href");
                        if (checkURL(link)) {
                            links.add(link);
                        }
                    }
                }
                Thread.sleep(200);
            }
        } catch (Exception ex) {
            mainSite.setLastError(ex.toString());
            if (indexingService.isStopFlag()) {
                log.info("Индексация остановлена пользователем: {}", mainSite.getUrl());
                mainSite.setLastError("Индексация остановлена пользователем");
                mainSite.setStatus(Status.FAILED);
            }
            sitesRepository.save(mainSite);
            log.warn(ex.toString());
            //ex.printStackTrace();
        }
        return links;
    }

    private boolean checkURL(String url) {
        return url.startsWith(mainSite.getUrl()) && (url.endsWith("/") || url.endsWith(".html"));
    }

    public boolean addNewURL(String url, int statusCode, String content) throws IOException {
        if (indexingService.isStopFlag()) {
            return false;
        }
        String pathLink = url.substring(mainSite.getUrl().length()-1);
        Page page = pagesRepository.findByPathLinkAndSite(pathLink, mainSite);
        if(page == null) {
            page = new Page();
            page.setPathLink(pathLink);
            page.setCode(statusCode);
            page.setContent(content);
            page.setSite(mainSite);
            page.setIndexes(new HashSet<>());
            pagesRepository.save(page);
            mainSite.setStatusTime(LocalDateTime.now());
            sitesRepository.save(mainSite);
            if (statusCode < 400) {
                LemmaFinder creatingLemmas = LemmaFinder.getInstance();
                Map<String, Integer> mapLemmas = new HashMap<>(creatingLemmas.collectLemmas(content));
                Set<String> setLemmas = new HashSet<>(mapLemmas.keySet());
                addLemmasAndIndexes(mapLemmas, setLemmas, page);
            }
            return true;
        }
        return false;
    }

    private synchronized void addLemmasAndIndexes(Map<String, Integer> mapLemmas, Set<String> setLemmas, Page page) {
        for (String newLemma : setLemmas) {
            if (indexingService.isStopFlag()) {
                return;
            }
            try {
                Lemma lemma;
                Optional<Lemma> optionalLemma = lemmasRepository.findByLemmaAndSite(newLemma, mainSite);
                if (optionalLemma.isEmpty()) {
                    lemma = new Lemma();
                    lemma.setSite(mainSite);
                    lemma.setLemma(newLemma);
                    lemma.setFrequency(1);
                    lemmasRepository.save(lemma);
                    log.info("add new lemma: {}", lemma);
                } else {
                    lemma = optionalLemma.get();
                    lemma.setFrequency(lemma.getFrequency() + 1);
                    lemmasRepository.save(lemma);
                    log.info("Frequency + 1: {}", lemma);
                }

                Index index = new Index();
                index.setPage(page);
                index.setLemmaId(lemma.getId());
                index.setRank(mapLemmas.get(newLemma));
                indexesRepository.save(index);
            } catch (Exception exception) {
                log.warn("find exception: " + mainSite.getName() + ", lemma='" + newLemma + " " + exception.getMessage());
            }
        }
    }

    public synchronized void deleteLemmas(Set<String> setLemmas) {
        /*for (String newLemma : setLemmas) {
            Lemma lemma = lemmasRepository.findByLemmaAndSite(newLemma, mainSite);
            if (lemma != null) {
                if (lemma.getFrequency() == 1) {
                    lemmasRepository.delete(lemma);
                } else {
                    lemma.setFrequency(lemma.getFrequency() - 1);
                    lemmasRepository.save(lemma);
                }
            }
        }*/
    }
}
