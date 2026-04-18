import { Component, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { Task } from '../models/task.model';
import { TaskService } from '../services/task.service';
import { TaskItemComponent } from '../task-item/task-item.component';

@Component({
  selector: 'app-task-list',
  imports: [RouterLink, TaskItemComponent],
  templateUrl: './task-list.component.html',
  styleUrl: './task-list.component.css',
})
export class TaskListComponent implements OnInit {
  readonly tasks = signal<Task[]>([]);
  readonly loadError = signal<string | null>(null);
  readonly deleteMessage = signal<string | null>(null);

  constructor(
    private readonly tasksApi: TaskService,
    readonly auth: AuthService,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.auth.loadCurrentUser().subscribe({
      next: (user) => {
        this.tasksApi.listByUser(user.id).subscribe({
          next: (list) => this.tasks.set(list),
          error: () => this.loadError.set('Не удалось загрузить задачи.'),
        });
      },
      error: () => {
        void this.router.navigate(['/login']);
      },
    });
  }

  onDeleteTask(id: number): void {
    this.deleteMessage.set(null);
    this.tasksApi.delete(id).subscribe({
      next: () => {
        this.tasks.update((list) => list.filter((t) => t.id !== id));
      },
      error: (err) => {
        if (err.status === 403) {
          this.deleteMessage.set('Удалять задачи может только администратор.');
        } else {
          this.deleteMessage.set('Не удалось удалить задачу.');
        }
      },
    });
  }
}
