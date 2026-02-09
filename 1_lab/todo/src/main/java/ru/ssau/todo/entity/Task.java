package ru.ssau.todo.entity;

import java.time.LocalDateTime;

public class Task {
    long id;
    String title;
    TaskStatus status;
    long createdBy;
    LocalDateTime createdAt;

    public Task(String title, long createdBy, TaskStatus status) {
        this.title = title;
        this.createdBy = createdBy;
        this.status = status;
        this.createdAt = LocalDateTime.now();
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

    public long getUserId() {
        return createdBy;
    }

    public String getTitle() {
        return this.title;
    }

    public TaskStatus getStatus() {
        return this.status;
    }

    public void setDataTime() {
        this.createdAt = LocalDateTime.now();
    }
}
