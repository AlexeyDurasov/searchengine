package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Setter
@Getter
@Entity
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;
    @Column(name = "site_id")
    private int siteId;
    @Column(columnDefinition = "TEXT")
    private String path;    // add index
    private int code;
    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;
}
