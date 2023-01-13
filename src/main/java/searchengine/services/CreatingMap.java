package searchengine.services;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.RecursiveTask;

public class CreatingMap extends RecursiveTask<Set<String>> {

    private static Set<String> visited = new TreeSet<>();
    private static String root;

    public CreatingMap(String root) {
        CreatingMap.root = root;
        System.out.println("constructor - " + root);
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

    private static boolean checkURL(String url) {
        return url.startsWith(root) && url.endsWith("/");
    }

    private synchronized boolean addNewURL(String url) throws InterruptedException {
        boolean addNewURL = visited.add(url);
        if (addNewURL) {
            System.out.println("every - " + url);
            //Thread.sleep(1000);
        }
        return addNewURL;
    }

    @Override
    protected Set<String> compute() {
        Set<CreatingMap> tasks = new LinkedHashSet<>();
        for (String link : parsePage(root)) {
            CreatingMap creatingMap = new CreatingMap(link);
            tasks.add(creatingMap);
            System.out.println("tasks.add   - " + root);
        }
        for (CreatingMap task : tasks) {
            task.fork();
        }
        for (CreatingMap task : tasks) {
            task.join();
        }
        return visited;
    }
}
