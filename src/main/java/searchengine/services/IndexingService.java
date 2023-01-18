package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingService {

    private final SitesList sites;
    private final SitesRepository sitesRepository;
    private final PagesRepository pagesRepository;

    public void startIndexing() throws InterruptedException {

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

        List<Thread> threads = new ArrayList<>();
        long countSites = sitesRepository.count();
        for (int i = 1+1; i <= countSites-1; i++) {
            int finalI = i;
            threads.add(new Thread(()-> {
                System.out.println(Thread.currentThread());
                Site site = sitesRepository.findById(finalI).get();
                String url = site.getUrl();
                CreatingMap creatingMap = new CreatingMap(url, sitesRepository, pagesRepository);
                ForkJoinPool forkJoinPool = new ForkJoinPool();
                String result = forkJoinPool.invoke(creatingMap);
                if (result.equals("Task completed")) {
                    site.setStatus(Status.INDEXED);
                    sitesRepository.save(site);
                } else {
                    site.setStatus(Status.FAILED);
/*или result*/      site.setLastError(System.err.toString());
                    sitesRepository.save(site);
                }
            }));
        }
        threads.forEach(Thread::start);
        for (Thread thread : threads) {
            thread.join();
        }
        //Thread.currentThread().join();
    }
}
