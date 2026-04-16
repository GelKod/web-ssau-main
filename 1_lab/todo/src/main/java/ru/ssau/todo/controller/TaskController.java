package ru.ssau.todo.controller;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.ssau.todo.dto.TaskDto;
import ru.ssau.todo.service.TaskService;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ResponseEntity<List<TaskDto>> findAll(@RequestParam long userId,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to) {
        if (from == null) {
            from = LocalDateTime.of(2000, 1, 1, 0, 0);
        }
        if (to == null) {
            to = LocalDateTime.of(2100, 12, 31, 23, 59, 59);
        }
        return ResponseEntity.ok(taskService.findAll(from, to, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDto> findById(@PathVariable Long id) {
        Optional<TaskDto> task = taskService.findById(id);
        return task.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TaskDto> createTask(@RequestBody TaskDto task) {
        TaskDto savedTask = taskService.create(task);
        return ResponseEntity.created(URI.create("tasks/" + savedTask.getId())).body(savedTask);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateTask(@PathVariable long id, @RequestBody TaskDto task) {
        task.setId(id);
        taskService.updateTask(task);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable long id) {
        taskService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/active/count")
    public ResponseEntity<Long> countTasks(@RequestParam long userId) {
        long count = taskService.countActiveTasksByUserId(userId);
        return ResponseEntity.ok(count);
    }
}
