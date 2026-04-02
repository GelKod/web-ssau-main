package ru.ssau.todo.repository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.ssau.todo.entity.Task;
import ru.ssau.todo.entity.TaskStatus;
import ru.ssau.todo.exception.TaskNotFoundException;

@Repository
@Profile("jdbc")
public class TaskJdbcRepository implements TaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public TaskJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private RowMapper<Task> taskRowMapper = (rs, rowNum) -> {
        Task task = new Task(
                rs.getString("title"),
                rs.getLong("created_by"),
                TaskStatus.valueOf(rs.getString("status")));

        task.setId(rs.getLong("id"));

        Timestamp timestamp = rs.getTimestamp("created_at");
        LocalDateTime createdAt = timestamp.toLocalDateTime();

        return task;
    };

    @Override
    public Task create(Task task) {

        String sql = """
                INSERT INTO task(title,status,created_by,created_at)
                VALUES (?,?,?,?)
                RETURNING id
                """;

        //String sql ="";

        Long id = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                task.getTitle(),
                task.getStatus().name(),
                task.getCreatedBy(),
                Timestamp.valueOf(task.getCreatedAt()));

        task.setId(id);

        return task;
    }

    @Override
    public Optional<Task> findById(long id) {

        String sql = "SELECT * FROM task WHERE id = ?";

        List<Task> tasks = jdbcTemplate.query(sql, taskRowMapper, id);

        if (tasks.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(tasks.get(0));
    }

    @Override
    public List<Task> findAll(LocalDateTime from, LocalDateTime to, long userId) {

        String sql = """
                SELECT * FROM task
                WHERE created_by = ?
                AND created_at >= ?
                AND created_at <= ?
                """;

        return jdbcTemplate.query(
                sql,
                taskRowMapper,
                userId,
                Timestamp.valueOf(from),
                Timestamp.valueOf(to));
    }

    @Override
    public void update(Task task) throws TaskNotFoundException {

        String sql = """
                UPDATE task
                SET title = ?, status = ?
                WHERE id = ?
                """;

        int rows = jdbcTemplate.update(
                sql,
                task.getTitle(),
                task.getStatus().name(),
                task.getId());

        if (rows == 0) {
            throw new TaskNotFoundException(Long.toString(task.getId()));
        }
    }

    @Override
    public void deleteById(long id) {

        String sql = "DELETE FROM task WHERE id = ?";

        jdbcTemplate.update(sql, id);
    }

    @Override
    public long countActiveTasksByUserId(long userId) {

        String sql = """
                SELECT COUNT(*)
                FROM task
                WHERE created_by = ?
                AND (status = 'OPEN' OR status = 'IN_PROGRESS')
                """;

        Long count = jdbcTemplate.queryForObject(sql, Long.class, userId);

        return count;
    }
}