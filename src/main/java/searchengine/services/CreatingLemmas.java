package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class CreatingLemmas {
    private HashMap<String, Integer> mapLemmas = new HashMap<>();
    private String contentPage;

    public HashMap<String, Integer> addLemmas(String contentPage) throws IOException {
        LuceneMorphology luceneMorph = new RussianLuceneMorphology();
        List<String> morphInfo = luceneMorph.getMorphInfo(contentPage);
        String[] words = morphInfo.get(0).split(" ");
        String noWord = words[words.length-1];
        switch (noWord) {
            case "МЕЖД", "СОЮЗ", "ПРЕДЛ", "ЧАСТ" -> mapLemmas.put(noWord, 1);
            default -> {
                List<String> wordBaseForms = luceneMorph.getNormalForms(contentPage);
                for (String word : wordBaseForms) {
                    mapLemmas.put(word, 1);
                }
            }
        }
        return mapLemmas;
    }
}
