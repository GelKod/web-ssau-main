import { DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Task, TaskStatus } from '../models/task.model';

@Component({
  selector: 'app-task-item',
  imports: [DatePipe, RouterLink],
  templateUrl: './task-item.component.html',
  styleUrl: './task-item.component.css',
})
export class TaskItemComponent {
  readonly task = input.required<Task>();
  readonly showDelete = input(false);
  readonly deleteTask = output<number>();

  statusClass(status: TaskStatus): string {
    switch (status) {
      case 'OPEN':
        return 'st-open';
      case 'IN_PROGRESS':
        return 'st-progress';
      case 'DONE':
        return 'st-done';
      case 'CLOSED':
        return 'st-closed';
      default:
        return '';
    }
  }

  onDelete(): void {
    this.deleteTask.emit(this.task().id);
  }
}
