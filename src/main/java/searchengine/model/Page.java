package searchengine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Setter
@Getter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(indexes = {@Index(name = "path", columnList = "path_id")})
public class Page {
    @Id
    @GeneratedValue
    private int id;
    @Column(name = "site_id", nullable = false)
    private int siteId;
    @Column(name = "path_id", nullable = false)
    private int pathId;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String path;
    private int code;
    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
