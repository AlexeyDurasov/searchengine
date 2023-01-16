package searchengine.services;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

import java.io.IOException;
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

    public Set<String> parsePage(String url) {
        Document doc;
        Set<String> links = new TreeSet<>();
        try {
            doc = Jsoup.connect(url).maxBodySize(0).get();
            Elements elements = doc.select("a[href]");

            for (Element element : elements) {
                String link = element.absUrl("href");
                if (checkURL(link) && addNewURL(link)) {
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

    private synchronized boolean addNewURL(String url) throws InterruptedException {
        if(pagesRepository.findByPathLink(url) == null) {
            Page page = new Page(/*pagesRepository.count() + 1,
                    sitesRepository.*/);
            page.setPathLink(url);
            pagesRepository.save(page);
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
