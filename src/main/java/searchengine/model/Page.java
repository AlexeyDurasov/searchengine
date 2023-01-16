package searchengine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;

@Setter
@Getter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(indexes = @Index(name = "path_id", columnList = "path_link", unique = true))
public class Page implements Serializable{
    @Id
    @GeneratedValue
    private int id;
    @Column(name = "site_id", nullable = false)
    private int siteId;
    //@JoinColumn(name = "path_id", nullable = false)
    //private int pathId;
    @Column(name = "path_link",columnDefinition = "TEXT", nullable = false)
    private String pathLink;
    private int code;
    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
