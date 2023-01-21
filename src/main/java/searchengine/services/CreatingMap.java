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
public class CreatingMap extends RecursiveTask<Set<String>> {

    private static String mainSite = "mainSite";
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
    protected Set<String> compute() {
        if (mainSite.equals("mainSite")) {
            mainSite = root;
        }
        System.out.println("every start - " + Thread.currentThread() + " - " + Thread.currentThread().getThreadGroup() + " - " + root);
        Set<CreatingMap> tasks = new LinkedHashSet<>();
        Set<String> pageLinks = parsePage(root);
        for (String link : pageLinks) {
            CreatingMap creatingMap = new CreatingMap(link, sitesRepository, pagesRepository);
            tasks.add(creatingMap);
            //System.out.println("tasks.add  - root = " + root);
            //System.out.println("             link = " + link);
        }
        for (CreatingMap task : tasks) {
            task.fork();
            //System.out.println("fork - " + task.root);
        }
        for (CreatingMap task : tasks) {
            //System.out.println("start join - " + Thread.currentThread() + task.root);
            task.join();
            //System.out.println("stop  join - " + Thread.currentThread() + task.root);
        }
        System.out.println("every stop - " + Thread.currentThread() + " - " + Thread.currentThread().getThreadGroup() + " - " + root);
        return pageLinks;
    }

    public Set<String> parsePage(String url) {
        long start = System.currentTimeMillis();
        System.out.println("start parsePage - " + url);
        Set<String> links = new TreeSet<>();
        try {
            if (checkURL(url)) {
                Connection connection = Jsoup.connect(url).maxBodySize(0);
                Document doc = connection.get();
                //System.out.println("open url - " + url);

                if (addNewURL(url, connection.execute().statusCode(), doc)) {
                    //System.out.println("add new url  - " + url);

                    Elements elements = doc.select("a[href]");

                    for (Element element : elements) {
                        String link = element.absUrl("href");
                        //System.out.println("find element - " + link);

                        if (checkURL(link)) {
                            Thread.sleep(200);
                            connection = Jsoup.connect(link).maxBodySize(0);
                            doc = connection.get();
                            //System.out.println("open  link  -  " + link);

                            if (addNewURL(link, connection.execute().statusCode(), doc)) {
                                links.add(link);
                                //System.out.println("add new link - " + link);
                            } else {
                                //System.out.println("already have link - " + link);
                            }
                        } else {
                            //System.out.println("another link - " + link);
                        }
                    }
                    Thread.sleep(200);
                } else {
                    //System.out.println("already have url - " + url);
                }
            } else {
                //System.out.println("another url  - " + url);
            }
        } catch (HttpStatusException ex) {
            return new TreeSet<>();
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
        }
        System.out.println("end parsePage - " + url);
        System.out.println("time working  - " + (System.currentTimeMillis() - start) + " ms");
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
                    root,
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
