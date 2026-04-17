package ru.ssau.todo.dto;

import ru.ssau.todo.entity.Task;
import ru.ssau.todo.entity.TaskStatus;

import java.time.LocalDateTime;

public class TaskDto {

    private long id;
    private String title;
    private TaskStatus status;
    private long createdBy;
    private LocalDateTime createdAt;

    public TaskDto() {
    }

    public TaskDto(long id, String title, TaskStatus status, long createdBy, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public TaskDto(String title, long id, TaskStatus status){
        this.title = title;
        this.id = id;
        this.status = status;
    }

    public TaskDto(String title, long id){
        this.title = title;
        this.id = id;
        this.status = TaskStatus.OPEN;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}