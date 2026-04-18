import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../environments/environment';

const USER_KEY = 'todo_username';
const PASS_KEY = 'todo_password';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const loginUrl = `${environment.apiUrl}/auth/login`;
  const path = req.url.split('?')[0];
  if (req.method === 'POST' && (path === loginUrl || path.endsWith('/auth/login'))) {
    return next(req);
  }

  const username = sessionStorage.getItem(USER_KEY);
  const password = sessionStorage.getItem(PASS_KEY);
  if (username && password) {
    const credentials = btoa(`${username}:${password}`);
    const authReq = req.clone({
      setHeaders: { Authorization: `Basic ${credentials}` },
    });
    return next(authReq);
  }

  return next(req);
};
