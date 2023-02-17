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
import searchengine.model.Status;
import searchengine.repositories.LemmasRepository;
import searchengine.repositories.SitesRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sites;
    private final SitesRepository sitesRepository;
    private final LemmasRepository lemmasRepository;
    private final IndexingService indexingService;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteConfig> sitesList = sites.getSites();
        boolean clearRepo = false;
        if (sitesRepository.count() != sitesList.size()) {
            lemmasRepository.deleteAll();
            sitesRepository.deleteAll();
            clearRepo = true;
        }
        for (SiteConfig site : sitesList) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            Site siteRepo;
            if (clearRepo) {
                siteRepo = indexingService.creatingSite(site, Status.FAILED);
            } else {
                siteRepo = sitesRepository.findByUrl(site.getUrl());
            }
            int pages = siteRepo.getPages().size();
            int lemmas = siteRepo.getLemmas().size();
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(siteRepo.getStatus().toString());
            item.setError(siteRepo.getLastError());
            LocalDateTime statusTime = siteRepo.getStatusTime();
            Instant instant = statusTime.toInstant(ZoneOffset.of("+03:00"));
            item.setStatusTime(instant.toEpochMilli());
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
