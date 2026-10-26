package data_centers.brasil.model;


import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "noticia")
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String url;

    @Column(nullable = false)
    private String fonte;

    private String categoria;

    @Column(name = "data_publicacao")
    private OffsetDateTime dataPublicacao;

    @Column(name = "data_coleta")
    private OffsetDateTime dataColeta;

    @Column(name = "imagem_url")
    private String imagemUrl;

    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFonte() {
        return fonte;
    }

    public void setFonte(String fonte) {
        this.fonte = fonte;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public OffsetDateTime getDataPublicacao() {
        return dataPublicacao;
    }

    public void setDataPublicacao(OffsetDateTime dataPublicacao) {
        this.dataPublicacao = dataPublicacao;
    }

    public OffsetDateTime getDataColeta() {
        return dataColeta;
    }

    public void setDataColeta(OffsetDateTime dataColeta) {
        this.dataColeta = dataColeta;
    }

    public String getImagemUrl() {
        return imagemUrl;
    }

    public void setImagemUrl(String imagemUrl) {
        this.imagemUrl = imagemUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

