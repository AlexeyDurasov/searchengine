package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.config.Connect;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;
import searchengine.repositories.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;
    private final Connect connect;
    private final SitesRepository sitesRepository;
    private final PagesRepository pagesRepository;
    private final IndexesRepository indexesRepository;
    private final LemmasRepository lemmasRepository;
    private List<Thread> threads = new ArrayList<>();
    private IndexingResponse indexingResponse = new IndexingResponse();
    private boolean stopFlag = false;

    public boolean isStopFlag() {
        return stopFlag;
    }

    @Override
    public IndexingResponse getStartIndexing() {
        if (threads.isEmpty()) {
            lemmasRepository.deleteAll();
            sitesRepository.deleteAll();
            stopFlag = false;
            startIndexing();
            indexingResponse.setResult(true);
            indexingResponse.setError(null);
        } else {
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация уже запущена");
        }
        return indexingResponse;
    }

    @Override
    public Site creatingSite(SiteConfig siteConfig, Status status) {
        Site site = new Site();
        site.setStatus(status);
        site.setStatusTime(LocalDateTime.now());
        site.setUrl(siteConfig.getUrl());
        site.setName(siteConfig.getName());
        site.setPages(new HashSet<>());
        site.setLemmas(new HashSet<>());
        sitesRepository.save(site);
        return site;
    }

    private void startIndexing() {
        for (int index = 0; index < sites.getSites().size(); index++) {
            Site site = creatingSite(sites.getSites().get(index), Status.INDEXING);
            Thread thread = new Thread(() -> {
                CreatingMap creatingMap = new CreatingMap(
                        site, site.getUrl(), connect, this,
                        sitesRepository, pagesRepository,
                        indexesRepository, lemmasRepository);
                ForkJoinPool forkJoinPool = new ForkJoinPool();
                forkJoinPool.execute(creatingMap);
                while (Thread.currentThread().isAlive() && forkJoinPool.getActiveThreadCount() > 0) {
                    if (Thread.currentThread().isInterrupted()) {
                        forkJoinPool.shutdownNow();
                        break;
                    }
                }
                if (!Thread.currentThread().isInterrupted()) {
                    site.setStatus(Status.INDEXED);
                    sitesRepository.save(site);
                }
            });
            thread.setName(site.getUrl());
            threads.add(thread);
        }
        threads.forEach(Thread::start);
    }

    @Override
    public IndexingResponse getStopIndexing() {
        if (!threads.isEmpty()) {
            stopFlag = true;
            for (Thread thread : threads) {
                thread.interrupt();
            }
            threads.clear();
            indexingResponse.setResult(true);
            indexingResponse.setError(null);
        } else {
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация не запущена");
        }
        return indexingResponse;
    }

    @Override
    public IndexingResponse getIndexingPage(String url) {
        boolean indexingPage = false;
        for (SiteConfig siteConfig : sites.getSites()) {
            if (url.contains(siteConfig.getUrl())) {
                try {
                    Connection connection = Jsoup.connect(url).userAgent(connect.getUserAgent())
                            .referrer(connect.getReferrer()).maxBodySize(0);
                    int statusCode = connection.execute().statusCode();
                    if (statusCode < 400) {
                        String content = connection.get().toString();
                        indexingPage(siteConfig, url, statusCode, content);
                    }
                } catch (Exception ex) {
                    Site site = sitesRepository.findByUrl(siteConfig.getUrl());
                    if (site != null) {
                        site.setLastError(ex.toString());
                        sitesRepository.save(site);
                    }
                    //ex.printStackTrace();
                }
                indexingPage = true;
                break;
            }
        }
        if (indexingPage) {
            indexingResponse.setResult(true);
            indexingResponse.setError(null);
        } else {
            indexingResponse.setResult(false);
            indexingResponse.setError(
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }
        return indexingResponse;
    }

    private void indexingPage(SiteConfig siteConfig, String url, int statusCode, String content) {
        Thread thread = new Thread(() -> {
            Site site = sitesRepository.findByUrl(siteConfig.getUrl());
            try {
                Page page = new Page();
                String pathLink = url.substring(siteConfig.getUrl().length() - 1);
                boolean newPage = false;
                if (site == null) {
                    site = creatingSite(siteConfig, Status.INDEXING);
                    page.setPathLink(pathLink);
                    page.setCode(statusCode);
                    page.setContent(content
                            /*Files.readString(Paths.get("D:/install/IntelliJ IDEA/ДЗ/из стрима.txt"))*/);
                    page.setSite(site);
                    page.setIndexes(new HashSet<>());
                    pagesRepository.save(page);
                    newPage = true;
                }
                CreatingMap creatingMap = new CreatingMap(
                        site, url, connect, this,
                        sitesRepository, pagesRepository,
                        indexesRepository, lemmasRepository);
                page = pagesRepository.findByPathLinkAndSite(pathLink, site);
                if (page != null && !newPage) {
                    LemmaFinder creatingLemmas = LemmaFinder.getInstance();
                    Map<String, Integer> mapLemmas = new HashMap<>(creatingLemmas.collectLemmas(page.getContent()));
                    Set<String> setLemmas = new HashSet<>(mapLemmas.keySet());
                    creatingMap.deleteLemmas(setLemmas);
                    pagesRepository.delete(page);
                }
                //String content = pagesRepository.findByPathLink(url).getContent(); //connection.get().toString();
                creatingMap.addNewURL(url, statusCode, content);
                site.setStatus(Status.INDEXED);
                sitesRepository.save(site);
            } catch (Exception ex) {
                if (site != null) {
                    site.setLastError(ex.toString());
                    site.setStatus(Status.FAILED);
                    sitesRepository.save(site);
                }
                //ex.printStackTrace();
            }
        });
        thread.start();
    }
}
