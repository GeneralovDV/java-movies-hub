package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MoviesHandler extends BaseHttpHandler implements HttpHandler {
    private final MoviesStore store;
    private final Gson gson = new Gson();

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();

        if (method.equalsIgnoreCase("GET")) {
            handleGet(ex);
            return;
        }
        if (method.equalsIgnoreCase("POST")) {
            handlePost(ex);
            return;
        }

        if (method.equalsIgnoreCase("DELETE") && path.matches("/movies/\\d+")) {
            int id = Integer.parseInt(path.replace("/movies/", ""));
            handleDelete(ex, id);
            return;
        }

        sendError(ex, 405, "Method Not Allowed");
    }

    private void handleGet(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery();

        if (path.equals("/movies")) {
            if (query == null || !query.startsWith("year=")) {
                sendJson(ex, 200, gson.toJson(store.getAllMovies()));
            } else {
                handleGetByYear(ex, query);
            }
        } else if (path.matches("/movies/\\d+")) {
            int id = Integer.parseInt(path.replace("/movies/", ""));
            handleGetById(ex, id);
        } else {
            sendError(ex, 400, "Некорректный путь");
        }
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");

        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            sendError(ex, 415, "Unsupported Media Type");
            return;
        }

        InputStreamReader isr = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder jsonBuilder = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            jsonBuilder.append(line);
        }

        String json = jsonBuilder.toString().trim();

        if (json.isEmpty()) {
            sendError(ex, 400, "Тело запроса пусто");
            return;
        }

        try {
            JsonElement jsonElement = new Gson().fromJson(json, JsonElement.class);
            if (jsonElement == null || !jsonElement.isJsonObject()) {
                sendError(ex, 400, "Некорректный JSON");
                return;
            }
        } catch (JsonSyntaxException e) {
            sendError(ex, 400, "Некорректный JSON");
            return;
        }

        try {
            Movie movie = gson.fromJson(json, Movie.class);
            List<String> errors = new ArrayList<>();

            if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
                errors.add("название не должно быть пустым");
            } else if (movie.getTitle().length() > 100) {
                errors.add("длина названия не должна превышать 100 символов");
            }

            int currentYear = Year.now().getValue();
            if (movie.getYear() < 1888 || movie.getYear() > currentYear + 1) {
                errors.add("год должен быть между 1888 и " + (currentYear + 1));
            }

            if (!errors.isEmpty()) {
                sendError(ex, 422, "Ошибка валидации", errors);
                return;
            }

            Movie created = store.addMovie(movie);
            sendJson(ex, 201, gson.toJson(created));
        } catch (Exception e) {
            sendError(ex, 400, "Некорректный JSON");
        }
    }

    private void handleDelete(HttpExchange ex, int id) throws IOException {
        if (store.containsMovie(id)) {
            store.removeMovie(id);
            sendNoContent(ex);
        } else {
            sendError(ex, 404, "Фильм не найден");
        }
    }

    private void handleGetById(HttpExchange ex, int id) throws IOException {
        Optional<Movie> movie = store.getMovieById(id);
        if (movie.isPresent()) {
            sendJson(ex, 200, gson.toJson(movie.get()));
        } else {
            sendError(ex, 404, "Фильм не найден");
        }
    }

    private void handleGetByYear(HttpExchange ex, String query) throws IOException {
        String yearStr = query.replace("year=", "");
        try {
            int year = Integer.parseInt(yearStr);
            List<Movie> movies = store.getMoviesByYear(year);
            sendJson(ex, 200, gson.toJson(movies));
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный параметр запроса — 'year'");
        }
    }

    protected void sendError(HttpExchange ex, int status, String error) throws IOException {
        sendError(ex, status, error, Collections.emptyList());
    }

}

