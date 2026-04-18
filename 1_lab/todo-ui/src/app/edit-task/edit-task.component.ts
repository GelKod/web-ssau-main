import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TASK_STATUSES, TaskStatus } from '../models/task.model';
import { TaskService } from '../services/task.service';

@Component({
  selector: 'app-edit-task',
  imports: [ReactiveFormsModule],
  templateUrl: './edit-task.component.html',
  styleUrl: './edit-task.component.css',
})
export class EditTaskComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly tasksApi = inject(TaskService);

  readonly statuses = TASK_STATUSES;
  readonly isCreate = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly saveError = signal<string | null>(null);

  private taskId: number | null = null;

  readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(500)]],
    status: ['OPEN' as TaskStatus, Validators.required],
  });

  ngOnInit(): void {
    const create = this.route.snapshot.data['create'] === true;
    this.isCreate.set(create);
    if (create) {
      return;
    }
    const raw = this.route.snapshot.paramMap.get('taskId');
    const id = raw ? Number(raw) : NaN;
    if (!Number.isFinite(id)) {
      this.loadError.set('Некорректный идентификатор задачи.');
      return;
    }
    this.taskId = id;
    this.tasksApi.getById(id).subscribe({
      next: (task) => {
        this.form.patchValue({ title: task.title, status: task.status });
      },
      error: () => this.loadError.set('Задача не найдена или нет доступа.'),
    });
  }

  submit(): void {
    this.saveError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { title, status } = this.form.getRawValue();
    if (this.isCreate()) {
      this.tasksApi.create({ title, status }).subscribe({
        next: () => void this.router.navigate(['/tasks']),
        error: () => this.saveError.set('Не удалось создать задачу.'),
      });
    } else if (this.taskId != null) {
      this.tasksApi.update(this.taskId, { title, status }).subscribe({
        next: () => void this.router.navigate(['/tasks']),
        error: () => this.saveError.set('Не удалось сохранить задачу.'),
      });
    }
  }

  cancel(): void {
    void this.router.navigate(['/tasks']);
  }
}
