package data_centers.brasil;

import com.sun.net.httpserver.HttpServer;
import data_centers.brasil.service.NewsCollectorService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class NewsCollectorServiceTests {
    @Test
    void parsesGoogleRpcWithoutTruncatingEscapedQueryParameters() {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        String url = "https://example.com/article?a=1&b=hello%20world";
        String result = mapper.writeValueAsString(java.util.List.of("garturlres", url, 1));
        String body = ")]}'\n\n123\n" + mapper.writeValueAsString(
                java.util.List.of(java.util.List.of("wrb.fr", "Fbv4je", result)));
        assertEquals(url, ReflectionTestUtils.invokeMethod(new NewsCollectorService(null), "parseGoogleArticleUrl", body));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "news.live", matches = "true")
    void resolvesRealFeedArticleAndExtractsItsImage() throws Exception {
        String feedUrl = "https://news.google.com/rss/search?q=data+center+Brasil&hl=pt-BR&gl=BR&ceid=BR:pt-419";
        try (var reader = new com.rometools.rome.io.XmlReader(java.net.URI.create(feedUrl).toURL())) {
            var feed = new com.rometools.rome.io.SyndFeedInput().build(reader);
            var service = new NewsCollectorService(null);
            String original = ReflectionTestUtils.invokeMethod(service, "resolverUrlOriginal", feed.getEntries().getFirst().getLink());
            assertNotNull(original, "Google deve retornar o link da reportagem");
            String image = ReflectionTestUtils.invokeMethod(service, "extrairImagem", original);
            assertNotNull(image, "A reportagem deve fornecer uma imagem");
            assertTrue(image.startsWith("http"));
        }
    }

    @Test
    void extractsEachArticlesOwnImageAndFollowsRedirects() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/first", exchange -> {
            exchange.getResponseHeaders().add("Location", "/article");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/article", exchange -> {
            byte[] html = "<meta property='og:image' content='/images/first.jpg'>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, html.length);
            try (var output = exchange.getResponseBody()) { output.write(html); }
        });
        server.createContext("/second", exchange -> {
            byte[] html = "<meta property='og:image' content=''><meta name='twitter:image' content='/images/second.jpg'>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, html.length);
            try (var output = exchange.getResponseBody()) { output.write(html); }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            NewsCollectorService service = new NewsCollectorService(null);
            assertEquals(base + "/article", ReflectionTestUtils.invokeMethod(service, "resolverUrlOriginal", base + "/first"));
            assertEquals(base + "/images/first.jpg", ReflectionTestUtils.invokeMethod(service, "extrairImagem", base + "/first"));
            assertEquals(base + "/images/second.jpg", ReflectionTestUtils.invokeMethod(service, "extrairImagem", base + "/second"));
        } finally {
            server.stop(0);
        }
    }
}
