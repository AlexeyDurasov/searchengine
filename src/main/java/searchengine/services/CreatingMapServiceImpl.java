package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import searchengine.config.Connect;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexesRepository;
import searchengine.repositories.LemmasRepository;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveAction;

@RequiredArgsConstructor
public class CreatingMapServiceImpl extends RecursiveAction implements CreatingMapService {

    //private final IndexingService indexingService;
    private final Site mainSite;
    private final String root;
    private final Connect connect;
    private final SitesRepository sitesRepository;
    private final PagesRepository pagesRepository;
    private final IndexesRepository indexesRepository;
    private final LemmasRepository lemmasRepository;

    /*public CreatingMapServiceImpl(Site mainSite, String root, IndexingService indexingService) {
        this.mainSite = mainSite;
        this.root = root;
        this.indexingService = indexingService;
    }*/

    @Override
    protected void compute() {
        Set<CreatingMapServiceImpl> tasks = new HashSet<>();
        Set<String> pageLinks = parsePage(root);
        for (String link : pageLinks) {
            CreatingMapServiceImpl creatingMapServiceImpl = new CreatingMapServiceImpl(
                    mainSite, link, connect, sitesRepository, pagesRepository,
                    indexesRepository, lemmasRepository);
            tasks.add(creatingMapServiceImpl);
        }
        for (CreatingMapServiceImpl task : tasks) {
            task.fork();
        }
        for (CreatingMapServiceImpl task : tasks) {
            task.join();
        }
    }

    private Set<String> parsePage(String url) {
        long start = System.currentTimeMillis();
        System.out.println("start parsePage - " + url);
        Set<String> links = new HashSet<>();
        try {
            if (checkURL(url)) {
                Connection connection = Jsoup.connect(url).userAgent(connect.getUserAgent())
                        .referrer(connect.getReferrer()).maxBodySize(0);
                Document doc = connection.get();
                Elements elements = doc.select("a[href]");
                for (Element element : elements) {
                    String link = element.absUrl("href");
                    if (checkURL(link)) {
                        Thread.sleep(200);
                        connection = Jsoup.connect(link).userAgent(connect.getUserAgent())
                                .referrer(connect.getReferrer()).maxBodySize(0);
                        String content = connection.get().toString();
                        int statusCode = connection.execute().statusCode();
                        if (addNewURL(link, statusCode, content)) {
                            links.add(link);
                            System.out.println("add new link - " + link);
                        }
                    }
                }
                Thread.sleep(200);
            }
        } catch (Exception ex) {
            mainSite.setLastError(ex.toString());
            sitesRepository.save(mainSite);
            ex.printStackTrace();
        }
        System.out.println("end parsePage - " + url);
        System.out.println("time working  - " + (System.currentTimeMillis() - start) + " ms");
        return links;
    }

    private boolean checkURL(String url) {
        return url.startsWith(mainSite.getUrl()) && url.endsWith("/");
    }

    public synchronized boolean addNewURL(String url, int statusCode, String content) throws IOException {
        String pathLink = url.substring(mainSite.getUrl().length()-1);
        Page page;
        if (pathLink.equals("/")) {
            page = pagesRepository.findByPathLinkAndSite(pathLink, mainSite);
        } else {
            page = pagesRepository.findByPathLink(pathLink);
        }
        if(page == null) {
            page = new Page();
            page.setPathLink(pathLink);
            page.setCode(statusCode);
            page.setContent(content);
            page.setSite(mainSite);
            page.setIndexes(new HashSet<>());
            pagesRepository.save(page);
            mainSite.setStatusTime(LocalDateTime.now());
            sitesRepository.save(mainSite);
            if (statusCode < 400) {
                addLemmasAndIndexes(content, page);
            }
            return true;
        }
        return false;
    }

    private synchronized void addLemmasAndIndexes(String content, Page page) throws IOException {
        LemmaFinder creatingLemmas = LemmaFinder.getInstance();
        Map<String, Integer> mapLemmas = new HashMap<>(creatingLemmas.collectLemmas(content));
        Set<String> setLemmas = new HashSet<>(mapLemmas.keySet());
        for (String newLemma : setLemmas) {
            Lemma lemma = lemmasRepository.findByLemma(newLemma);
            if (lemma == null) {
                lemma = new Lemma();
                lemma.setSite(mainSite);
                lemma.setLemma(newLemma);
                lemma.setFrequency(1);
            } else {
                lemma.setFrequency(lemma.getFrequency() + 1);
            }
            lemmasRepository.save(lemma);

            Index index = new Index();
            index.setPage(page);
            index.setLemmaId(lemma.getId());
            index.setRank(mapLemmas.get(newLemma));
            indexesRepository.save(index);
        }
    }

    public void deleteLemmas(String content) throws IOException {
        LemmaFinder creatingLemmas = LemmaFinder.getInstance();
        Map<String, Integer> mapLemmas = new HashMap<>(creatingLemmas.collectLemmas(content));
        Set<String> setLemmas = new HashSet<>(mapLemmas.keySet());
        for (String newLemma : setLemmas) {
            Lemma lemma = lemmasRepository.findByLemma(newLemma);
            if (lemma != null) {
                if (lemma.getFrequency() == 1) {
                    lemmasRepository.delete(lemma);
                } else {
                    lemma.setFrequency(lemma.getFrequency() - 1);
                    lemmasRepository.save(lemma);
                }
            }
        }
    }
}