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

    public Set<String> parsePage(String url) {
        Set<String> links = new TreeSet<>();
        try {
            Connection connection = Jsoup.connect(url).maxBodySize(0);
            Document doc = connection.get();
            Elements elements = doc.select("a[href]");

            for (Element element : elements) {
                String link = element.absUrl("href");
                if (checkURL(link) && addNewURL(link, connection.execute().statusCode(), doc)) {
                    links.add(link);
                    System.out.println("every link - " + link);
                }
            }
            Thread.sleep(200);
        } catch (HttpStatusException ex) {
            return new TreeSet<>();
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
        }
        return links;
    }

    private boolean checkURL(String url) {
        return url.startsWith(root) && url.endsWith("/");
    }

    private synchronized boolean addNewURL(String url, int statusCode, Document content) throws InterruptedException {
        if(pagesRepository.findByPathLink(url) == null) {
            String pathLink;
            String contentToString = content.toString();
            if (url.equals(root)) {
                pathLink = "/";
            } else {
                pathLink = url.substring(root.length()); // уточнить
            }
            Page page = new Page(
                    (int)pagesRepository.count() + 1,
                    sitesRepository.findByUrl(url).getId(),
                    pathLink,
                    statusCode,
                    contentToString);
            pagesRepository.save(page);
            Site site = sitesRepository.findByUrl(url);
            site.setStatusTime(LocalDateTime.now());
            sitesRepository.save(site);
            return true;
        }
        return false;
    }

    @Override
    protected String compute() {
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
}
