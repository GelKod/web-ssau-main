import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Task } from '../models/task.model';

@Injectable({ providedIn: 'root' })
export class TaskService {
  private readonly base = `${environment.apiUrl}/tasks`;

  constructor(private readonly http: HttpClient) {}

  listByUser(userId: number): Observable<Task[]> {
    const params = new HttpParams().set('userId', String(userId));
    return this.http.get<Task[]>(this.base, { params });
  }

  getById(id: number): Observable<Task> {
    return this.http.get<Task>(`${this.base}/${id}`);
  }

  create(task: Partial<Task>): Observable<Task> {
    return this.http.post<Task>(this.base, task);
  }

  update(id: number, task: Partial<Task>): Observable<void> {
    return this.http.put<void>(`${this.base}/${id}`, { ...task, id });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
