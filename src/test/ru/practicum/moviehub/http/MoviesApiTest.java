package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {
    private static final MoviesStore store = new MoviesStore();
    private static final MoviesServer server = new MoviesServer(store, 8080);
    private static final String BASE = "http://localhost:8080";
    private static HttpClient client;
    private static final Gson gson = new Gson();

    @BeforeAll
    static void beforeAll() {
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @BeforeEach
    void clearStore() {
        store.clear();
    }

    @Test
    @DisplayName("GET /movies — возвращает пустой список, если нет фильмов")
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {

        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .header("Content-Type", "application/json")
                .header("charset", "UTF-8")
                .build();

        var responseBodyHandler = HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        var resp = client.send(req, responseBodyHandler);

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");
        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    @DisplayName("POST /movies — добавляет фильм при корректных данных")
    void postMovie_valid_returns201() throws Exception {
        String json = """
                {
                  "title": "Matrix",
                  "year": 1999
                }
                """;

        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();

        assertEquals(201, resp.statusCode());
        assertTrue(body.contains("\"id\":"));
        assertTrue(body.contains("\"title\":\"Matrix\""));
        assertTrue(body.contains("\"year\":1999"));
    }

    @Test
    @DisplayName("POST /movies — возвращает 422 при некорректных данных")
    void postMovie_emptyTitle_returns422() throws Exception {
        String json = """
                {
                  "title": "",
                  "year": 2010
                }
                """;

        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();

        assertEquals(422, resp.statusCode());
        assertTrue(body.contains("\"error\":\"Ошибка валидации\""));
        assertTrue(body.contains("\"details\":[\"название не должно быть пустым\"]"));
    }

    @Test
    @DisplayName("POST /movies — возвращает ошибку при слишком длинном title")
    void postMovie_longTitle_returns422() throws Exception {
        String longTitle = "A".repeat(101);
        String json = """
                {
                  "title": "%s",
                  "year": 2010
                }
                """.formatted(longTitle);

        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();

        assertEquals(422, resp.statusCode());
        assertTrue(body.contains("\"error\":\"Ошибка валидации\""));
        assertTrue(body.contains("\"details\":[\"длина названия не должна превышать 100 символов\"]"));
    }

    @Test
    @DisplayName("POST /movies — возвращает ошибку при неверном year")
    void postMovie_invalidYear_returns422() throws Exception {
        String json = """
                {
                  "title": "Matrix",
                  "year": 1000
                }
                """;

        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();

        assertEquals(422, resp.statusCode());
        assertTrue(body.contains("\"error\":\"Ошибка валидации\""));
        assertTrue(body.contains("\"details\":[\"год должен быть между 1888 и"));
    }

    @Test
    @DisplayName("POST /movies — возвращает ошибку при неправильном Content-Type")
    void postMovie_invalidContentType_returns415() throws Exception {
        String json = """
                {
                  "title": "Matrix",
                  "year": 2010
                }
                """;

        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .header("Content-Type", "text/plain")
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();

        assertEquals(415, resp.statusCode());
        assertTrue(body.contains("\"error\":\"Unsupported Media Type\""));
    }

    @Test
    @DisplayName("POST /movies — возвращает ошибку при некорректном JSON")
    void postMovie_invalidJson_returns400() throws Exception {
        String json = """
                {
                  "title" "Matrix",
                  "year": 2010
                }
                """;

        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();
        System.out.println(body);

        assertEquals(400, resp.statusCode());
        assertTrue(body.contains("\"error\":\"Некорректный JSON\""));
    }

    @Test
    @DisplayName("GET /movies/{id} — возвращает фильм по существующему id")
    void getMovieById_returns200() throws Exception {
        int id = addMovieAndGetId("Inception", 2010);

        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + id))
                .GET()
                .header("Content-Type", "application/json")
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();

        assertEquals(200, resp.statusCode());
        assertTrue(body.contains("\"title\":\"Inception\""));
    }

    @Test
    @DisplayName("GET /movies/{id} — возвращает ошибку, если фильм не найден")
    void getMovieById_notFound_returns404() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .GET()
                .header("Content-Type", "application/json")
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();

        assertEquals(404, resp.statusCode());
        assertTrue(body.contains("\"error\":\"Фильм не найден\""));
    }

    @Test
    @DisplayName("GET /movies/{id} — возвращает ошибку, если id не число")
    void getMovieById_invalidId_returns400() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .header("Content-Type", "application/json")
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();
        System.out.println(body);

        assertEquals(400, resp.statusCode());
        assertTrue(body.contains("\"error\":\"Некорректный путь\""));
    }

    @Test
    @DisplayName("DELETE /movies/{id} — удаляет фильм по существующему id")
    void deleteMovieById_returns204() throws Exception {
        int id = addMovieAndGetId("Titanic", 1997);

        var delReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + id))
                .DELETE()
                .build();

        var delResp = client.send(delReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(204, delResp.statusCode());

        var getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + id))
                .GET()
                .header("Content-Type", "application/json")
                .build();

        var getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(404, getResp.statusCode());
    }

    @Test
    @DisplayName("DELETE /movies/{id} — возвращает ошибку, если фильм не найден")
    void deleteMovieById_notFound_returns404() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .DELETE()
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();

        assertEquals(404, resp.statusCode());
        assertTrue(body.contains("\"error\":\"Фильм не найден\""));
    }

    @Test
    @DisplayName("GET /movies?year=YYYY — возвращает фильмы указанного года")
    void getMoviesByYear_returnsList() throws Exception {
        addMovie("Inception", 2010);
        addMovie("Interstellar", 2010);
        addMovie("Matrix", 1999);

        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2010"))
                .GET()
                .header("Content-Type", "application/json")
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();

        assertTrue(body.contains("\"title\":\"Inception\""));
        assertTrue(body.contains("\"title\":\"Interstellar\""));
        assertFalse(body.contains("\"title\":\"Matrix\""));
    }

    @Test
    @DisplayName("GET /movies?year=YYYY — возвращает пустой список, если фильмов с таким годом нет")
    void getMoviesByYear_emptyList_returnsEmptyArray() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2020"))
                .GET()
                .header("Content-Type", "application/json")
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals("[]", resp.body().trim());
    }

    @Test
    @DisplayName("GET /movies?year=YYYY — возвращает ошибку, если параметр year не число")
    void getMoviesByYear_invalidParam_returns400() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .GET()
                .header("Content-Type", "application/json")
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();
        System.out.println(body);

        assertEquals(400, resp.statusCode());
        String decodedBody = body.replace("\\u0027", "'");
        assertTrue(decodedBody.contains("Некорректный параметр запроса — 'year'"));
    }

    @Test
    @DisplayName("Все успешные ответы содержат Content-Type — application/json; charset=UTF-8")
    void allSuccessResponses_haveCorrectContentType() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals("application/json; charset=UTF-8", resp.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    @DisplayName("Ошибки возвращают объект с полем error и при необходимости details")
    void allErrorResponses_haveErrorStructure() throws Exception {
        String json = "{\"title\": \"\", \"year\": 2010}";

        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();

        assertTrue(body.contains("\"error\":\"Ошибка валидации\""));
        assertTrue(body.contains("\"details\":[\"название не должно быть пустым\"]"));
    }

    @Test
    @DisplayName("При неподдерживаемом HTTP-методе возвращается 405 Method Not Allowed")
    void unsupportedMethod_returns405() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();

        assertEquals(405, resp.statusCode());
        assertTrue(body.contains("\"error\":\"Method Not Allowed\""));
    }

    private int addMovieAndGetId(String title, int year) throws Exception {
        String json = String.format("""
                {
                  "title": "%s",
                  "year": %d
                }
                """, title, year);

        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return gson.fromJson(resp.body(), Map.class).get("id").toString().charAt(0) - '0';
    }

    private void addMovie(String title, int year) throws Exception {
        String json = String.format("""
                {
                  "title": "%s",
                  "year": %d
                }
                """, title, year);

        var req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}