package ru.ssau.todo.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import ru.ssau.todo.dto.TaskDto;
import ru.ssau.todo.entity.Task;
import ru.ssau.todo.entity.TaskStatus;
import ru.ssau.todo.entity.User;
import ru.ssau.todo.repository.TaskRepository;
import ru.ssau.todo.repository.UserRepository;

@Service
public class TaskService {

    private static final String TOO_MANY_ACTIVE_TASKS_MESSAGE = "У пользователя больше 10 активных задач";
    private static final String DELETE_RECENT_TASK_MESSAGE = "Невозможно удалить задачу, созданную менее 5 минут назад.";

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TaskDto create(TaskDto taskDto) {
        User user = getUser(taskDto.getCreatedBy());
        validateActiveTasksLimit(user.getId());

        Task task = new Task(taskDto.getTitle(), user, resolveStatus(taskDto.getStatus()));
        Task savedTask = taskRepository.save(task);

        return toDto(savedTask);
    }

    @Transactional(readOnly = true)
    public Optional<TaskDto> findById(long id) {
        return taskRepository.findById(id).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<TaskDto> findAll(LocalDateTime from, LocalDateTime to, long userId) {
        return taskRepository.findByCreatedAtBetweenAndUserId(from, to, userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void updateTask(TaskDto taskDto) {
        Task existingTask = taskRepository.findById(taskDto.getId())
                .orElseThrow(() -> new EntityNotFoundException("Task not found"));

        Long createdById = taskDto.getCreatedBy() != null ? taskDto.getCreatedBy() : existingTask.getCreatedBy().getId();
        User user = getUser(createdById);
        TaskStatus newStatus = resolveStatus(taskDto.getStatus());

        boolean wasActive = isActive(existingTask.getStatus());
        boolean isActive = isActive(newStatus);
        boolean userChanged = !existingTask.getCreatedBy().getId().equals(user.getId());

        if ((userChanged && isActive) || (!wasActive && isActive)) {
            validateActiveTasksLimit(user.getId());
        }

        existingTask.setTitle(taskDto.getTitle());
        existingTask.setStatus(newStatus);
        existingTask.setCreatedBy(user);
        taskRepository.save(existingTask);
    }

    @Transactional
    public void deleteById(long id) {
        Optional<Task> optionalTask = taskRepository.findById(id);
        if (optionalTask.isEmpty()) {
            return;
        }

        Task task = optionalTask.get();
        if (task.getCreatedAt().plusMinutes(5).isAfter(LocalDateTime.now())) {
            throw new IllegalStateException(DELETE_RECENT_TASK_MESSAGE);
        }

        taskRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public long countActiveTasksByUserId(long userId) {
        return taskRepository.countActiveTasksByUserId(userId);
    }

    private void validateActiveTasksLimit(Long userId) {
        long activeCount = taskRepository.countActiveTasksByUserId(userId);
        if (activeCount >= 10) {
            throw new IllegalStateException(TOO_MANY_ACTIVE_TASKS_MESSAGE);
        }
    }

    private User getUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("createdBy is required");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private TaskStatus resolveStatus(TaskStatus status) {
        return status == null ? TaskStatus.OPEN : status;
    }

    private boolean isActive(TaskStatus status) {
        return status == TaskStatus.OPEN || status == TaskStatus.IN_PROGRESS;
    }

    private TaskDto toDto(Task task) {
        TaskDto dto = new TaskDto();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setStatus(task.getStatus());
        dto.setCreatedBy(task.getCreatedBy().getId());
        dto.setCreatedAt(task.getCreatedAt());
        return dto;
    }
}
