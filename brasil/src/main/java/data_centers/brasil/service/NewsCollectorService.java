package data_centers.brasil.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import data_centers.brasil.model.News;
import data_centers.brasil.repository.NewsRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLConnection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

@Service
public class NewsCollectorService {

    private final NewsRepository repository;

    private static final String FEED_URL =
            "https://news.google.com/rss/search"
                    + "?q=data+center+Brasil"
                    + "&hl=pt-BR&gl=BR&ceid=BR:pt-419";

    public NewsCollectorService(NewsRepository repository) {
        this.repository = repository;
    }

    public int collect() throws Exception {

        URLConnection connection =
                URI.create(FEED_URL).toURL().openConnection();

        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

        SyndFeed feed;

        try (var stream = connection.getInputStream();
             var reader = new XmlReader(stream)) {

            feed = new SyndFeedInput().build(reader);
        }

        int saved = 0;

        for (SyndEntry entry : feed.getEntries()) {

            String title = entry.getTitle();
            String urlGoogle = entry.getLink();
            String fonte = "Fonte não identificada";

            if (title == null || urlGoogle == null
                    || title.isBlank() || urlGoogle.isBlank()) {
                continue;
            }

            // Evita salvar notícias duplicadas
            var existente = repository.findByUrl(urlGoogle);
            News news = existente.orElseGet(News::new);

            news.setTitulo(
                    title.length() > 500
                            ? title.substring(0, 500)
                            : title
            );

            news.setUrl(urlGoogle);
            news.setCategoria("Geral");
            news.setDataColeta(OffsetDateTime.now());
            news.setStatus("PUBLICADA");

            // 1. Recebe o link individual da notícia no Google
            System.out.println("Notícia: " + title);
            System.out.println("URL Google: " + urlGoogle);

            // 2. Tenta descobrir a URL original da reportagem
            String urlOriginal = resolverUrlOriginal(urlGoogle);

            // 3. Extrai a imagem do site que publicou a notícia
            String imagemUrl = null;

            if (urlOriginal != null && !urlOriginal.isBlank()) {
                System.out.println("URL original: " + urlOriginal);

                imagemUrl = extrairImagem(urlOriginal);
            } else {
                System.out.println("Não foi possível resolver a URL original.");
            }

            // 4. Define a imagem da notícia
            if (imagemUrl != null) {
                news.setImagemUrl(imagemUrl);
            }

            // 5. Define a data de publicação
            if (entry.getPublishedDate() != null) {
                news.setDataPublicacao(
                        entry.getPublishedDate()
                                .toInstant()
                                .atOffset(ZoneOffset.UTC)
                );
            }

            // 6. Identifica o veículo da notícia
            if (entry.getSource() != null
                    && entry.getSource().getTitle() != null
                    && !entry.getSource().getTitle().isBlank()) {

                fonte = entry.getSource().getTitle();
            }

            news.setFonte(
                    fonte.length() > 150
                            ? fonte.substring(0, 150)
                            : fonte
            );

            // 7. Salva no banco
            repository.save(news);
            if (existente.isEmpty()) {
                saved++;
            }

            System.out.println("Notícia salva com sucesso!");
            System.out.println("------------------------------");
        }

        return saved;
    }

    private String extrairImagem(String urlNoticia) {
        try {
            var response = Jsoup.connect(urlNoticia)
                    .userAgent("Mozilla/5.0")
                    .followRedirects(true)
                    .timeout(10000)
                    .execute();

            Document doc = response.parse();

            String urlFinal = response.url().toString();

            System.out.println("URL original do RSS: " + urlNoticia);
            System.out.println("URL final acessada: " + urlFinal);

            // Evita capturar a imagem genérica do Google Notícias
            String host = URI.create(urlFinal).getHost();

            if (host != null && (
                    host.equals("news.google.com") ||
                            host.endsWith(".news.google.com")
            )) {
                System.out.println("Ainda estamos no Google Notícias.");
                return null;
            }

            for (Element imagem : doc.select(
                    "meta[property=og:image], meta[property=og:image:url], "
                            + "meta[name=twitter:image], meta[property=twitter:image]")) {
                if (imagem.attr("content").isBlank()) {
                    continue;
                }
                String imagemUrl = imagem.absUrl("content");

                if (imagemUrl.startsWith("https://") || imagemUrl.startsWith("http://")) {
                    System.out.println("Imagem encontrada: " + imagemUrl);
                    return imagemUrl;
                }
            }

            System.out.println("Imagem não encontrada.");

        } catch (Exception e) {
            System.out.println("Erro ao extrair imagem: " + e.getMessage());
        }

        return null;
    }

    private String resolverUrlOriginal(String urlGoogle) {
        try {
            var response = Jsoup.connect(urlGoogle)
                    .userAgent("Mozilla/5.0")
                    .followRedirects(true)
                    .timeout(10000)
                    .execute();

            String urlFinal = response.url().toString();
            String host = URI.create(urlFinal).getHost();

            if (host != null &&
                    !host.equals("news.google.com") &&
                    !host.endsWith(".news.google.com")) {

                return urlFinal;
            }

            // Google News pode entregar uma página intermediária em vez de um redirect.
            Document document = response.parse();
            Element metadata = document.selectFirst("[data-n-a-sg][data-n-a-ts]");
            if (metadata != null) {
                String path = URI.create(urlGoogle).getPath();
                String id = path.substring(path.lastIndexOf('/') + 1);
                String signature = metadata.attr("data-n-a-sg");
                String timestamp = metadata.attr("data-n-a-ts");
                if (id.matches("[A-Za-z0-9_-]+")
                        && signature.matches("[A-Za-z0-9_-]+")
                        && timestamp.matches("[0-9]+")) {
                    String arguments = "[\"garturlreq\",[[\"en-US\",\"US\",[\"FINANCE_TOP_INDICES\",\"WEB_TEST_1_0_0\"],"
                            + "null,null,1,1,\"US:en\",null,180,null,null,null,null,null,0,null,null,[1608992183,723341000]],"
                            + "\"en-US\",\"US\",1,[2,3,4,8],1,0,\"655000234\",0,0,null,0],\""
                            + id + "\"," + timestamp + ",\"" + signature + "\"]";
                    String payload = "[[[\"Fbv4je\",\""
                            + arguments.replace("\\", "\\\\").replace("\"", "\\\"")
                            + "\",null,\"generic\"]]]";
                    String body = Jsoup.connect("https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je")
                            .userAgent("Mozilla/5.0")
                            .timeout(10000)
                            .ignoreContentType(true)
                            .data("f.req", payload)
                            .method(org.jsoup.Connection.Method.POST)
                            .execute().body();
                    String articleUrl = parseGoogleArticleUrl(body);
                    if (articleUrl != null) {
                        String articleHost = URI.create(articleUrl).getHost();
                        if (articleHost != null && !articleHost.equals("news.google.com")
                                && !articleHost.endsWith(".google.com")) {
                            return articleUrl;
                        }
                    }
                }
            }

            return null;

        } catch (Exception e) {
            System.out.println(
                    "Erro ao resolver URL: " + e.getMessage()
            );
            return null;
        }
    }

    private String parseGoogleArticleUrl(String body) {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        // O protocolo inclui um prefixo anti-XSSI e, às vezes, o tamanho de cada bloco.
        for (String line : body.split("\\R")) {
            if (!line.startsWith("[[")) {
                continue;
            }
            for (var frame : mapper.readTree(line)) {
                if (frame.size() < 3 || !"Fbv4je".equals(frame.get(1).asText())
                        || !frame.get(2).isTextual()) {
                    continue;
                }
                var result = mapper.readTree(frame.get(2).asText());
                if (result.size() > 1 && "garturlres".equals(result.get(0).asText())) {
                    return result.get(1).asText();
                }
            }
        }
        return null;
    }
}
