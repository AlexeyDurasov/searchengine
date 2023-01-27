package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService{

    private final SitesList sites;
    private final SitesRepository sitesRepository;
    private final PagesRepository pagesRepository;
    private List<Thread> threads = new ArrayList<>();
    private boolean indexing = false;
    private IndexingResponse indexingResponse = new IndexingResponse();

    @Override
    public IndexingResponse getStartIndexing() {
        for (Site site : sitesRepository.findAll()) {
            if (site.getStatus().equals(Status.INDEXING)) {
                indexing = true;
                break;
            }
        }
        if (!indexing) {
            clearingTheDatabaseAndCreatingWebsites();
            startIndexing();
            indexingResponse.setResult(true);
            indexingResponse.setError(null);
            return indexingResponse;
        } else {
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация уже запущена");
            return indexingResponse;
        }
    }

    private void startIndexing() {
        long countSites = sitesRepository.count();
        for (int i = 1+1; i <= countSites-1; i++) {
            int finalI = i;
            threads.add(new Thread(()-> {
                Site site = sitesRepository.findById(finalI).get();
                CreatingMap creatingMap = new CreatingMap(site.getUrl(), sitesRepository, pagesRepository);
                ForkJoinPool forkJoinPool = new ForkJoinPool();
                forkJoinPool.invoke(creatingMap);
                site.setStatus(Status.INDEXED);
                sitesRepository.save(site);
            }));
        }
        threads.forEach(Thread::start);
    }

    private void clearingTheDatabaseAndCreatingWebsites() {
        sitesRepository.deleteAll();
        pagesRepository.deleteAll();
        for (int i = 0; i < sites.getSites().size(); i++) {
            Site site = new Site(
                    i+1,
                    Status.INDEXING,
                    LocalDateTime.now(),
                    "lastError",
                    sites.getSites().get(i).getUrl(),
                    sites.getSites().get(i).getName());
            sitesRepository.save(site);
        }
    }

    @Override
    public IndexingResponse getStopIndexing() {
        if (indexing) {
            for (Site site : sitesRepository.findAll()) {
                if (site.getStatus().equals(Status.INDEXING)) {
                    threads.get(site.getId() - 1).interrupt();
                    site.setLastError("Индексация остановлена пользователем");
                    site.setStatus(Status.FAILED);
                    sitesRepository.save(site);
                }
            }
            indexingResponse.setResult(true);
            indexingResponse.setError(null);
            return indexingResponse;
        } else {
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация не запущена");
            return indexingResponse;
        }
    }
}
