package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Connect;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;
import searchengine.repositories.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService{

    private final SitesList sites;
    private final Connect connect;
    private final SitesRepository sitesRepository;
    private final PagesRepository pagesRepository;
    private final IndexesRepository indexesRepository;
    private final LemmasRepository lemmasRepository;
    private List<Thread> threads = new ArrayList<>();
    private IndexingResponse indexingResponse = new IndexingResponse();

    @Override
    public IndexingResponse getStartIndexing() {
        if (threads.size() == 0) {
            lemmasRepository.deleteAll();
            sitesRepository.deleteAll();
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
            Thread thread = new Thread(()-> {
                /*CreatingMapServiceImpl creatingMap = new CreatingMapServiceImpl(
                        site, site.getUrl(), connect,
                        sitesRepository, pagesRepository,
                        indexesRepository, lemmasRepository);
                ForkJoinPool forkJoinPool = new ForkJoinPool();
                forkJoinPool.invoke(creatingMap);
                site.setStatus(Status.INDEXED);
                sitesRepository.save(site);*/
                String url = site.getUrl();
                Status status = site.getStatus();
                if (url.equals("https://skillbox.ru/")) {
                    while (status.equals(Status.INDEXING)) {
                        System.out.println("sleep " + Thread.currentThread().getName());
                        for (int i = 1; i > 0; i++);
                        status = sitesRepository.findByUrl(url).getStatus();
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
        if (threads.size() > 0) {
            for (Thread thread : threads) {
                thread.interrupt();
                Site site = sitesRepository.findByUrl(thread.getName());
                if (site.getStatus().equals(Status.INDEXING)) {
                    site.setLastError("Индексация остановлена пользователем");
                    site.setStatus(Status.FAILED);
                    sitesRepository.save(site);
                }
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
                indexingPage = true;
                //Connection connection = Jsoup.connect(url).userAgent(connect.getUserAgent())
                //        .referrer(connect.getReferrer()).maxBodySize(0);
                int statusCode = 200;//connection.execute().statusCode();
                if (statusCode < 400) {
                    //String content = connection.get().toString();
                    indexingPage(siteConfig, url, statusCode/*, content*/);
                }
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

    private void indexingPage(SiteConfig siteConfig, String url, int statusCode/*, String content*/) {
        Thread thread = new Thread(() -> {
            try {
                Site site = sitesRepository.findByUrl(siteConfig.getUrl());
                Page page = new Page();
                String pathLink = url.substring(siteConfig.getUrl().length() - 1);
                boolean newPage = false;
                if (site == null) {
                    site = creatingSite(siteConfig, Status.FAILED);
                    page.setPathLink(url);
                    page.setCode(statusCode);
                    page.setContent(/*content*/
                            Files.readString(Paths.get("D:/install/IntelliJ IDEA/ДЗ/из стрима.txt")));
                    page.setSite(site);
                    page.setIndexes(new HashSet<>());
                    pagesRepository.save(page);
                    newPage = true;
                }
                CreatingMapServiceImpl creatingMapServiceImpl = new CreatingMapServiceImpl(
                        site, url, connect,
                        sitesRepository, pagesRepository,
                        indexesRepository, lemmasRepository);
                page = pagesRepository.findByPathLinkAndSite(pathLink, site);
                if (page != null && !newPage) {
                    String content = page.getContent();
                    creatingMapServiceImpl.deleteLemmas(content);
                    pagesRepository.delete(page);
                }
                String content = pagesRepository.findByPathLink(url).getContent(); //connection.get().toString();
                creatingMapServiceImpl.addNewURL(url, statusCode, content);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        thread.setName(url);
        threads.add(thread);
        thread.start();
    }
}
