package ru.ssau.todo.repository;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.stereotype.Repository;

import ru.ssau.todo.entity.Task;

@Repository
public class TaskInMemoryRepository implements TaskRepository {

    private long _idGen = 1;

    private Map<Long, Task> tasks = new HashMap<>();

    public Task create(Task task) {
        try {
            task.setId(_idGen);
            task.setDataTime();
            tasks.put(_idGen, task);
            _idGen++;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Передана пустая задача", e);
        }
        return task;
    }

    public Optional<Task> findById(long id) {
        return Optional.ofNullable(tasks.get(id));
    }

    public List<Task> findAll(LocalDateTime from, LocalDateTime to, long userId) {
        List<Task> tmp = new LinkedList<>();

        for (Map.Entry<Long, Task> task : tasks.entrySet()) {
            Task t = task.getValue();
            LocalDateTime createdAt = t.getCreatedAt();
            boolean isAfterOrEqualFrom = !createdAt.isBefore(from);
            boolean isBeforeOrEqualTo = !createdAt.isAfter(to);
            if (t.getUserId() == userId
                    && isBeforeOrEqualTo
                    && isAfterOrEqualFrom) {
                tmp.add(t);
            }
        }
        return tmp;
    }

    public void update(Task task) throws Exception {
        tasks.replace(task.getId(), task);
    }

    public void deleteById(long id) {
        tasks.remove(id);
    }

    public long countActiveTasksByUserId(long userId) {
        int counter = 0;
        for (Map.Entry<Long, Task> task : tasks.entrySet()) {
            if ((task.getValue().getUserId() == userId)) {
                counter++;
            }
        }
        return counter;
    }
}
