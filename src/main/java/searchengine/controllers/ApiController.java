package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

import java.time.LocalDateTime;
import java.util.List;

// http://localhost:8080/
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final SitesList sites;
    private final SitesRepository sitesRepository;
    private final PagesRepository pagesRepository;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public String startIndexing() throws InterruptedException {
        String result = """
                {
                    'result':""";
        /*for (int i = 0; i < sites.getSites().size(); i++) {
            Site site = new Site(
                    i+1, Status.INDEXED, LocalDateTime.now(), "lastError",
                    sites.getSites().get(i).getUrl(),
                    sites.getSites().get(i).getName());
            sitesRepository.save(site);
        }*/
        /*for (int i = 1; i <= sitesRepository.count(); i++) {
            *//*sitesRepository.findById(i).get().setStatus(Status.INDEXED);
            sitesRepository.save(sitesRepository.findById(i).get());*//*
            if (sitesRepository.findById(i).get().getStatus().equals(Status.INDEXING)) {
                return result + """
                         false,
                            'error': "Индексация уже запущена"
                        }""";
            }
        }*/
        IndexingService indexingService = new IndexingService(sites, sitesRepository, pagesRepository);
        indexingService.startIndexing();

        return result + " true\n}";
    }

    @GetMapping("/stopIndexing")
    public String stopIndexing() {
        String result = """
                {
                    'result':""";
        boolean allSitesINDEXED = true;
        /*Site site = sitesRepository.findById(2).get();
        site.setStatus(Status.INDEXING);
        sitesRepository.save(site);*/
        long count = sitesRepository.count();
        for (int i = 1; i <= count; i++) {
            if (sitesRepository.findById(i).get().getStatus().equals(Status.INDEXING)) {
                allSitesINDEXED = false;
                break;
            }
        }
        if (allSitesINDEXED) {
            return result + """
                         false,
                            'error': "Индексация не запущена"
                        }""";
        }
        //StopIndexing...
        //IndexingService indexingService = new IndexingService(sites, sitesRepository, pagesRepository);
        //indexingService.startIndexing();
        return result + " true\n}";
    }
}
