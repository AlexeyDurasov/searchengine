package searchengine.services;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
                    i+1, Status.INDEXED, LocalDateTime.now(), "lastError",
                    sites.getSites().get(i).getUrl(),
                    sites.getSites().get(i).getName());
            sitesRepository.save(site);
        }

        System.out.println(Thread.currentThread());
        List<Thread> threads = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            int finalI = i;
            threads.add(new Thread(()-> {
                System.out.println(Thread.currentThread());
                String url = sitesRepository.findById(finalI).get().getUrl();
                CreatingMap creatingMap = new CreatingMap(url, sitesRepository, pagesRepository);
                ForkJoinPool forkJoinPool = new ForkJoinPool();
                String links = forkJoinPool.invoke(creatingMap);
                /*for (String link : links) {
                    pagesRepository.save(
                            new Page(1, finalI, finalI,
                                    link, 200, "content"
                            ));
                }*/
            }));
        }
        threads.forEach(Thread::start);
        for (Thread thread : threads) {
            thread.join();
        }
        //Thread.currentThread().join();
    }
}
