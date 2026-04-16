package ru.ssau.todo.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.ssau.todo.entity.Task;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query(value = """
            SELECT *
            FROM task
            WHERE created_by = :userId
              AND created_at >= :from
              AND created_at <= :to
            ORDER BY id
            """, nativeQuery = true)
    List<Task> findByCreatedAtBetweenAndUserId(@Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("userId") Long userId);

    @Query("""
            select count(t)
            from Task t
            where t.createdBy.id = :userId
              and (t.status = ru.ssau.todo.entity.TaskStatus.OPEN
                   or t.status = ru.ssau.todo.entity.TaskStatus.IN_PROGRESS)
            """)
    long countActiveTasksByUserId(@Param("userId") Long userId);
}
