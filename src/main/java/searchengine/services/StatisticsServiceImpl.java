package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.repositories.IndexesRepository;
import searchengine.repositories.LemmasRepository;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final Random random = new Random();
    private final SitesList sites;
    private final SitesRepository sitesRepository;
    private final PagesRepository pagesRepository;
    private final IndexesRepository indexesRepository;
    private final LemmasRepository lemmasRepository;

    @Override
    public StatisticsResponse getStatistics() {
        String[] statuses = { "INDEXED", "FAILED", "INDEXING" };
        String[] errors = {
                "Ошибка индексации: главная страница сайта не доступна",
                "Ошибка индексации: сайт не доступен",
                ""
        };

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteConfig> sitesList = sites.getSites();
        for (SiteConfig site : sitesList) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            Site siteRepo = sitesRepository.findByUrl(site.getUrl());
            int pages = siteRepo.getPages().size();//random.nextInt(1_000);
            int lemmas = siteRepo.getLemmas().size();//pages * random.nextInt(1_000);
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(siteRepo.getStatus().toString()/*statuses[i % 3]*/);
            item.setError(siteRepo.getLastError()/*errors[i % 3]*/);
            LocalDateTime statusTime = siteRepo.getStatusTime();
            Instant instant = statusTime.toInstant(ZoneOffset.UTC);
            item.setStatusTime(instant.toEpochMilli()/*System.currentTimeMillis() -
                    (random.nextInt(10_000))*/);
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
