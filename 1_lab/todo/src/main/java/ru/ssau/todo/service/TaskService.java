package ru.ssau.todo.service;

import org.springframework.stereotype.Service;
import ru.ssau.todo.entity.Task;
import ru.ssau.todo.entity.TaskStatus;
import ru.ssau.todo.repository.TaskRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Task create(Task task) {
        long userId = task.getCreatedBy();
        long activeCount = taskRepository.countActiveTasksByUserId(userId);
        if (activeCount >= 10) {
            throw new IllegalStateException("У пользователя больше 10 активных задач");
        }
        if (task.getStatus() == null) {
            task.setStatus(TaskStatus.OPEN);
        }

        return taskRepository.create(task);
    }

    public Optional<Task> findById(long id) {
        return taskRepository.findById(id);
    }

    public List<Task> findAll(LocalDateTime from, LocalDateTime to, long userId) {
        return taskRepository.findAll(from, to, userId);
    }

    public void updateTask(Task task) throws Exception {
        Optional<Task> existingTaskOpt = taskRepository.findById(task.getId());
        if (existingTaskOpt.isPresent()) {
            Task existing = existingTaskOpt.get();
            boolean wasActive = existing.getStatus() == TaskStatus.OPEN || existing.getStatus() == TaskStatus.IN_PROGRESS;
            boolean isActive = task.getStatus() == TaskStatus.OPEN || task.getStatus() == TaskStatus.IN_PROGRESS;

            if (!wasActive && isActive) {
                long activeCount = taskRepository.countActiveTasksByUserId(task.getCreatedBy());
                if (activeCount >= 10) {
                    throw new IllegalStateException("У пользователя больше 10 активных задач");
                }
            }
        }
        taskRepository.update(task);
    }

    public void deleteById(long id) {
        Optional<Task> optionalTask = taskRepository.findById(id);
        if (optionalTask.isEmpty()) {
            return;
        }

        Task task = optionalTask.get();
        LocalDateTime now = LocalDateTime.now();
        if (task.getCreatedAt().plusMinutes(5).isAfter(now)) {
            throw new IllegalStateException("Невозможно удалить задачу, созданную менее 5 минут назад.");
        }

        taskRepository.deleteById(id);
    }

    public long countActiveTasksByUserId(long userId) {
        return taskRepository.countActiveTasksByUserId(userId);
    }
}