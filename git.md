# 📘 Git — краткая методичка

## 1. Основные команды

| Команда | Описание |
|--------|----------|
| `git switch main` | Переключиться на ветку `main` |
| `git switch -c new-feature` | Создать новую ветку и сразу переключиться на неё |
| `git add .` | Добавить все изменения в индекс (stage) |
| `git add file.txt` | Добавить конкретный файл |
| `git commit -m "сообщение"` | Создать коммит |
| `git merge feature` | Влить ветку `feature` в текущую |
| `git push origin main` | Отправить ветку `main` в удалённый репозиторий |
| `git push -u origin feature` | Отправить новую ветку и связать с удалённой |
| `git pull` | Получить и применить изменения с удалённого репозитория |
| `git branch -d feature` | Удалить локальную ветку |
| `git restore file.txt` | Откатить файл к последнему коммиту |
| `git status` | Показать текущее состояние |
| `git log --oneline --graph` | Краткая история с визуализацией веток |

---

## 2. Частые сценарии

### 🚀 Создать ветку → закоммитить → отправить

```bash
git switch -c task-123
git add .
git commit -m "сделал задачу"
git push -u origin task-123
```
### 🔀 Влить ветку в main и отправить
```bash
git switch main
git merge task-123
git push origin main
```
### 🔄 Обновить main перед началом работы
```bash
git switch main
git pull
git switch -c new-branch
```
## 3. Полные примеры
### 📌 Пример 1: Полный цикл разработки
#### 1. Обновляем main
```bash
git switch main
git pull
```
#### 2. Создаём ветку
```bash
git switch -c add-login
```
#### 3. Работаем
```bash
echo "login form" > login.html
git add .
git commit -m "добавил форму логина"
```
#### 4. Вливаем изменения в main
```bash
git switch main
git merge add-login
```
#### 5. Пушим main
```bash
git push origin main
```
#### 6. Удаляем ветку (опционально)
```bash
git branch -d add-login
```
### 📌 Пример 2: Публикация ветки для команды
```bash
git switch -c experiment
```
#### ... работа и коммиты ...
```
git push -u origin experiment
```
Теперь другие разработчики могут работать с веткой:
```
git switch experiment
```
### ⚠️ Пример 3: Разрешение конфликта
```
git switch main
git pull
git merge feature-xyz
```
#### Возник конфликт → исправляем файлы вручную
```
git add .
git commit -m "merge feature-xyz"
git push origin main
```
# 💡 Полезные советы
- Всегда делай `git pull` перед началом работы
- Пушь ветки регулярно, чтобы не потерять прогресс
- Используй осмысленные сообщения коммитов
- Удаляй ненужные ветки после слияния
- Проверяй git status перед коммитом
# 📎 Мини-чеклист
1. Обновил main
2. Создал ветку
3. Сделал коммиты
4. Запушил ветку
5. Сделал merge
6. Запушил main
7. Удалил ветку (опционально)