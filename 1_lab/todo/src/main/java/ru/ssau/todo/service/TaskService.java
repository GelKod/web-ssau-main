package ru.ssau.todo.service;

import org.springframework.stereotype.Service;
import ru.ssau.todo.dto.TaskDto;
import ru.ssau.todo.entity.Task;
import ru.ssau.todo.entity.TaskStatus;
import ru.ssau.todo.entity.User;
import ru.ssau.todo.exception.TaskNotFoundException;
import ru.ssau.todo.repository.TaskRepository;
import ru.ssau.todo.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    public TaskDto create(TaskDto taskDto, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        long activeCount = taskRepository.countActiveTasksByUserId(user.getId());
        if (activeCount >= 10) {
            throw new IllegalStateException("User has more than 10 active tasks");
        }

        if (taskDto.getStatus() == null) {
            taskDto.setStatus(TaskStatus.OPEN);
        }

        Task task = new Task(taskDto.getTitle(), user, taskDto.getStatus());
        return toDto(taskRepository.save(task));
    }

    public Optional<TaskDto> findById(long id) {
        return taskRepository.findById(id).map(this::toDto);
    }

    public List<TaskDto> findAll(LocalDateTime from, LocalDateTime to, long userId) {
        return taskRepository.findAll(userId, from, to)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public void updateTask(TaskDto taskDto) {
        Task existingTask = taskRepository.findById(taskDto.getId())
                .orElseThrow(() -> new TaskNotFoundException(taskDto.getId()));

        User user = userRepository.findById(existingTask.getCreatedByUser().getId())
                .orElseThrow(() -> new TaskNotFoundException(existingTask.getCreatedByUser().getId()));

        boolean wasActive = existingTask.getStatus() == TaskStatus.OPEN
                || existingTask.getStatus() == TaskStatus.IN_PROGRESS;
        TaskStatus newStatus = taskDto.getStatus() == null ? existingTask.getStatus() : taskDto.getStatus();
        boolean isActive = newStatus == TaskStatus.OPEN || newStatus == TaskStatus.IN_PROGRESS;

        if (!wasActive && isActive) {
            long activeCount = taskRepository.countActiveTasksByUserId(user.getId());
            if (activeCount >= 10) {
                throw new IllegalStateException("User has more than 10 active tasks");
            }
        }

        if (taskDto.getTitle() != null) {
            existingTask.setTitle(taskDto.getTitle());
        }
        existingTask.setStatus(newStatus);
        existingTask.setCreatedByUser(user);

        taskRepository.save(existingTask);
    }

    public void deleteById(long id) {
        Optional<TaskDto> optionalTask = taskRepository.findById(id).map(this::toDto);
        if (optionalTask.isEmpty()) {
            return;
        }

        TaskDto task = optionalTask.get();
        LocalDateTime now = LocalDateTime.now();
        if (task.getCreatedAt().plusMinutes(5).isAfter(now)) {
            throw new IllegalStateException("Cannot delete task created less than 5 minutes ago");
        }

        taskRepository.deleteById(id);
    }

    public long countActiveTasksByUserId(long userId) {
        return taskRepository.countActiveTasksByUserId(userId);
    }

    public TaskDto toDto(Task task) {
        TaskDto dto = new TaskDto();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setStatus(task.getStatus());
        dto.setCreatedBy(task.getCreatedByUser().getId());
        dto.setCreatedAt(task.getCreatedAt());
        return dto;
    }
}
