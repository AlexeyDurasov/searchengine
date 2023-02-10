package searchengine.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

@Setter
@Getter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "sites")
public class Site {
    @Id
    //@GeneratedValue//(strategy = GenerationType.IDENTITY)
    private int id;
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
    private Status status;
    @Column(name = "status_time", nullable = false)
    @JsonFormat(pattern = "dd.MM.yyyy HH:mm:ss")
    private LocalDateTime statusTime;
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String url;
    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String name;
    @OneToMany(mappedBy = "site")
    private Set<Page> pages;
    @OneToMany(mappedBy = "site")
    private Set<Lemma> lemmas;

    public void addPage(Page page) {
        pages.add(page);
    }

    public void addLemma(Lemma lemma) {
        lemmas.add(lemma);
    }
}
