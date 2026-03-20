package ru.ssau.todo.entity;

import java.time.LocalDateTime;

public class Task {
    private long id;
    private String title;
    private TaskStatus status;
    private long createdBy;
    private LocalDateTime createdAt;

    public Task(String title, Long createdBy, TaskStatus status) {
        this.title = title;
        if (createdBy != null) {
            this.createdBy = createdBy;
        } else {
            this.createdBy = 0;
        }
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.id = 0;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(long createdBy) {
        this.createdBy = createdBy;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public TaskStatus getStatus() {
        return this.status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public void setDataTime() {
        this.createdAt = LocalDateTime.now();
    }
}
