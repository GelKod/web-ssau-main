package ru.ssau.todo.repository;

import java.util.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import ru.ssau.todo.entity.Task;

/**
 * Интерфейс репозитория для управления жизненным циклом сущностей {@link Task}.
 * Обеспечивает абстракцию над механизмом хранения данных.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {
    /**
     * Возвращает список всех задач конкретного пользователя, созданных в указанном
     * временном диапазоне.
     *
     * @param from   начальная граница даты создания (включительно).
     * @param to     конечная граница даты создания (включительно).
     * @param userId уникальный идентификатор пользователя-владельца.
     * @return список задач, соответствующих критериям поиска. Если ничего не
     *         найдено, возвращается пустой список.
     */
    @Query(value = "SELECT * FROM task t WHERE t.created_by = :userId AND t.created_at >= :from AND t.created_at <= :to", nativeQuery = true)
    List<Task> findAll(@Param("userId") Long userId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

    /**
     * Подсчитывает количество "активных" задач для конкретного пользователя.
     * Активной считается задача, находящаяся в статусе OPEN или IN_PROGRESS.
     *
     * @param userId идентификатор пользователя.
     * @return количество активных задач.
     */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.createdByUser.id = :userId AND t.status IN ('OPEN', 'IN_PROGRESS')")
    long countActiveTasksByUserId(@Param("userId") Long userId);
}
