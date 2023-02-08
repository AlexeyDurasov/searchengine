package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
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
    private final SitesRepository sitesRepository;
    private final PagesRepository pagesRepository;
    private final IndexesRepository indexesRepository;
    private final LemmasRepository lemmasRepository;
    private List<Thread> threads = new ArrayList<>();
    private IndexingResponse indexingResponse = new IndexingResponse();

    @Override
    public IndexingResponse getStartIndexing() {
        int countIndexingSites = 0;
        for (Site site : sitesRepository.findAll()) {
            if (site.getStatus().equals(Status.INDEXING)) {
                ++countIndexingSites;
                break;
            }
        }
        if (countIndexingSites == 0) {
            clearingDB();
            for (int index = 0; index < sites.getSites().size(); index++) {
                creatingSite(sites.getSites().get(index), index + 1);
            }
            startIndexing();
            indexingResponse.setResult(true);
            indexingResponse.setError(null);
        } else {
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация уже запущена");
        }
        return indexingResponse;
    }

    private void clearingDB() {
        sitesRepository.deleteAll();
        pagesRepository.deleteAll();
        indexesRepository.deleteAll();
        lemmasRepository.deleteAll();
    }

    private Site creatingSite(SiteConfig siteConfig, int index) {
        Site site = new Site(
                index,
                Status.INDEXING,
                LocalDateTime.now(),
                "lastError",
                siteConfig.getUrl(),
                siteConfig.getName());
        sitesRepository.save(site);
        return site;
    }

    private void startIndexing() {
        long countSites = sitesRepository.count();
        for (int i = 1; i <= countSites; i++) {
            int finalI = i;
            threads.add(new Thread(()-> {
                Site site = sitesRepository.findById(finalI).get();
                CreatingMap creatingMap = new CreatingMap(site, site.getUrl(),
                        sitesRepository, pagesRepository,
                        indexesRepository, lemmasRepository);
                ForkJoinPool forkJoinPool = new ForkJoinPool();
                forkJoinPool.invoke(creatingMap);
                site = sitesRepository.findById(finalI).get();
                site.setStatus(Status.INDEXED);
                sitesRepository.save(site);
            }));
        }
        threads.forEach(Thread::start);
    }

    @Override
    public IndexingResponse getStopIndexing() {
        int countIndexingSites = 0;
        for (Site site : sitesRepository.findAll()) {
            if (site.getStatus().equals(Status.INDEXING)) {
                threads.get(site.getId() - 1).interrupt();
                site.setLastError("Индексация остановлена пользователем");
                site.setStatus(Status.FAILED);
                sitesRepository.save(site);
                ++countIndexingSites;
            }
        }
        threads.clear();
        if (countIndexingSites > 0) {
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
                //Connection connection = Jsoup.connect(url).maxBodySize(0);
                int statusCode = 200;//connection.execute().statusCode();
                if (statusCode < 400) {
                    indexingPage(siteConfig, url, statusCode);
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

    private void indexingPage(SiteConfig siteConfig, String url, int statusCode) {
        Thread thread = new Thread(() -> {
            try {
                for (Thread threadSite : threads) {
                    threadSite.join();
                }
                Site site = sitesRepository.findByUrl(siteConfig.getUrl());
                Page page = new Page();
                String pathLink = url.substring(siteConfig.getUrl().length() - 1);
                if (site == null) {
                    site = creatingSite(siteConfig, (int)sitesRepository.count() + 1);
                    page.setPathLink(url);
                    page.setCode(statusCode);
                    page.setContent(Files.readString(Paths.get("D:/install/IntelliJ IDEA/ДЗ/из стрима.txt")));
                    page.setSite(site);
                    pagesRepository.save(page);
                }
                page = pagesRepository.findByPathLinkAndSite(pathLink, site);
                CreatingMap creatingMap = new CreatingMap(site, url,
                        sitesRepository, pagesRepository,
                        indexesRepository, lemmasRepository);
                if (page != null) {
                    String content = page.getContent();
                    creatingMap.deleteLemmasAndIndexes(content, page.getId());
                    pagesRepository.delete(page);
                }
                String content = pagesRepository.findByPathLink(url).getContent(); //connection.get().toString();
                creatingMap.addNewURL(url, statusCode, content);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();
    }
}