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

        System.out.println(Thread.currentThread());
        IndexingService indexingService = new IndexingService(sites, sitesRepository, pagesRepository);
        indexingService.startIndexing();

        return result + " true\n}";
    }

    @GetMapping("/stopIndexing")
    public String stopIndexing() {
        String result = """
                {
                    'result':""";
        for (int i = 1; i <= sitesRepository.count(); i++) {
            //sitesRepository.findById(i).get().setStatus(Status.INDEXED);
            if (sitesRepository.findById(i).get().getStatus().equals(Status.INDEXED)) {
                return result + """
                         false,
                            'error': "Индексация не запущена"
                        }""";
            }
        }
        //IndexingService indexingService = new IndexingService(sites, sitesRepository, pagesRepository);
        //indexingService.startIndexing();
        return result + " true\n}";
    }
}
