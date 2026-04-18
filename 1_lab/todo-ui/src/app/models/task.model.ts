export type TaskStatus = 'OPEN' | 'DONE' | 'IN_PROGRESS' | 'CLOSED';

export interface Task {
  id: number;
  title: string;
  status: TaskStatus;
  createdBy: number;
  createdAt: string;
}

export const TASK_STATUSES: TaskStatus[] = ['OPEN', 'IN_PROGRESS', 'DONE', 'CLOSED'];
