package searchengine.services;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.RecursiveTask;

//@Service
@RequiredArgsConstructor
public class CreatingMap extends RecursiveTask<String> {

    private static String mainSite;
    private final String root;
    private final SitesRepository sitesRepository;
    private final PagesRepository pagesRepository;

    @Transactional
    public Page findByPathLink(String path) {
        return pagesRepository.findByPathLink(path);
    }

    @Transactional
    public Site findByUrl(String url) {
        return sitesRepository.findByUrl(url);
    }

    @Override
    protected String compute() {
        mainSite = root;
        Set<CreatingMap> tasks = new LinkedHashSet<>();
        for (String link : parsePage(root)) {
            CreatingMap creatingMap = new CreatingMap(link, sitesRepository, pagesRepository);
            tasks.add(creatingMap);
            System.out.println("tasks.add  - " + root);
        }
        for (CreatingMap task : tasks) {
            task.fork();
        }
        for (CreatingMap task : tasks) {
            task.join();
        }
        return "Task completed";
    }

    public Set<String> parsePage(String url) {
        Set<String> links = new TreeSet<>();
        try {
            if (checkURL(url)) {
                Document doc = Jsoup.connect(url).maxBodySize(0).get();
                Elements elements = doc.select("a[href]");

                for (Element element : elements) {
                    Thread.sleep(200);
                    String link = element.absUrl("href");

                    if (checkURL(link)) {
                        Connection connection = Jsoup.connect(link).maxBodySize(0);
                        doc = connection.get();

                        if (addNewURL(link, connection.execute().statusCode(), doc)) {
                            links.add(link);
                            System.out.println("every link - " + link);
                        }
                    }
                }
                Thread.sleep(200);
            }
        } catch (HttpStatusException ex) {
            return new TreeSet<>();
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
        }
        return links;
    }

    private boolean checkURL(String url) {
        return url.startsWith(mainSite) && url.endsWith("/");
    }

    private synchronized boolean addNewURL(String url, int statusCode, Document content) throws InterruptedException {
        String pathLink = url.substring(mainSite.length()-1);
        Site site = sitesRepository.findByUrl(mainSite);
        Page page = pagesRepository.findByPathLink(pathLink);
        if(page == null ||
                (page.getPathLink().equals("/") && page.getSiteId() != site.getId())) {
            page = new Page(
                    (int)pagesRepository.count() + 1,
                    site.getId(),
                    pathLink,
                    statusCode,
                    url/*content.toString()*/);
            pagesRepository.save(page);
            site.setStatusTime(LocalDateTime.now());
            sitesRepository.save(site);
            return true;
        }
        return false;
    }
}
