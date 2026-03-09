package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class MoviesStore {
    private final Map<Integer, Movie> movies = new HashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);

    public List<Movie> getAllMovies() {
        return new ArrayList<>(movies.values());
    }

    public Optional<Movie> getMovieById(int id) {
        return Optional.ofNullable(movies.get(id));
    }

    public List<Movie> getMoviesByYear(int year) {
        List<Movie> result = new ArrayList<>();
        for (Movie m : movies.values()) {
            if (m.getYear() == year) {
                result.add(m);
            }
        }
        return result;
    }

    public Movie addMovie(Movie movie) {
        int id = idCounter.getAndIncrement();
        movie.setId(id);
        movies.put(id, movie);
        return movie;
    }

    public boolean removeMovie(int id) {
        return movies.remove(id) != null;
    }

    public boolean containsMovie(int id) {
        return movies.containsKey(id);
    }

    public void clear() {
        movies.clear();
    }
}