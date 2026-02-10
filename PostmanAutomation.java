import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostmanAutomation {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final String BASE_URL = "http://localhost:8080/tasks";
    private static DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static int passedTests = 0;
    private static int failedTests = 0;
    private static final String LINE = "=".repeat(60);
    private static final String SUB_LINE = "-".repeat(60);

    public static void main(String[] args) throws Exception {

        printHeader("ЗАПУСК POSTMAN-АВТОМАТИЗАЦИИ");
        System.out.println("URL сервера: " + BASE_URL);
        System.out.println("Время запуска: " + LocalDateTime.now().format(FORMATTER));
        System.out.println();

        createUsers();

        LocalDateTime testStartTime = LocalDateTime.now();
        System.out.println("Время начала тестов: " + testStartTime.format(FORMATTER));
        System.out.println();

        printTestSection("ТЕСТ 1: Создание первых 3 задач для пользователя 555");
        FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        createFirstTasks();

        System.out.println("\n" + SUB_LINE);
        System.out.println("Ожидание 10 секунд...");
        Thread.sleep(10000);

        LocalDateTime afterFirstTasks = LocalDateTime.now();

        printTestSection("ТЕСТ 2: Создание следующих 2 задач для пользователя 555");
        createSecondTasks();

        LocalDateTime afterSecondTasks = LocalDateTime.now();

        System.out.println("\n" + SUB_LINE);
        System.out.println("Ожидание 5 секунд...");
        Thread.sleep(5000);

        printTestSection("ТЕСТ 3: Создание последней задачи для пользователя 555");
        createThirdTask();

        LocalDateTime finalTime = LocalDateTime.now();

        printHeader("ТЕСТИРОВАНИЕ ФИЛЬТРАЦИИ ПО ДАТАМ");
        testFiltering(testStartTime, afterFirstTasks, afterSecondTasks, finalTime);

        printHeader("ТЕСТИРОВАНИЕ ОСНОВНЫХ ОПЕРАЦИЙ");
        testBasicOperations();

        printSummary();
    }

    private static void printHeader(String title) {
        System.out.println("\n" + LINE);
        System.out.println("  " + title);
        System.out.println(LINE);
    }

    private static void printTestSection(String title) {
        System.out.println("\n" + SUB_LINE);
        System.out.println("  " + title);
        System.out.println(SUB_LINE);
    }

    private static void testFiltering(LocalDateTime testStartTime, LocalDateTime afterFirstTasks,
            LocalDateTime afterSecondTasks, LocalDateTime finalTime) {

        System.out.println("Диапазоны времени для тестирования:");
        System.out.println("  Начало тестов:        " + testStartTime.format(FORMATTER));
        System.out.println("  После 1-й порции:     " + afterFirstTasks.format(FORMATTER));
        System.out.println("  После 2-й порции:     " + afterSecondTasks.format(FORMATTER));
        System.out.println("  Финальное время:      " + finalTime.format(FORMATTER));
        System.out.println();

        // 4.1. Показать ВСЕ задачи пользователя 555
        testFilter("4.1. ВСЕ задачи пользователя 555 (6 задач)",
                BASE_URL + "?userId=555",
                "Status: 200",
                ".*\"id\":14.*\"id\":15.*\"id\":16.*\"id\":17.*\"id\":18.*\"id\":19.*",
                true);

        // 4.2. Задачи, созданные ПОСЛЕ первых 10 секунд
        String fromAfterFirst = afterFirstTasks.format(FORMATTER);
        testFilter("4.2. Задачи созданные ПОСЛЕ " + fromAfterFirst,
                BASE_URL + "?userId=555&from=" + fromAfterFirst,
                "Status: 200",
                ".*\"id\":17.*\"id\":18.*\"id\":19.*",
                true);
        testFilter("4.2.1. Проверка отсутствия ранних задач",
                BASE_URL + "?userId=555&from=" + fromAfterFirst,
                "Status: 200",
                ".*\"id\":14.*\"id\":15.*\"id\":16.*",
                false);

        // 4.3. Задачи, созданные ДО второго набора
        String toBeforeSecond = afterFirstTasks.format(FORMATTER);
        testFilter("4.3. Задачи созданные ДО " + toBeforeSecond,
                BASE_URL + "?userId=555&to=" + toBeforeSecond,
                "Status: 200",
                ".*\"id\":14.*\"id\":15.*\"id\":16.*",
                true);

        // 4.4. Задачи в узком диапазоне (между первой и второй порцией)
        String narrowFrom = testStartTime.plusSeconds(5).format(FORMATTER);
        String narrowTo = afterFirstTasks.minusSeconds(5).format(FORMATTER);
        testFilter("4.4. Задачи в узком диапазоне (должно быть 0 задач)",
                BASE_URL + "?userId=555&from=" + narrowFrom + "&to=" + narrowTo,
                "Status: 200",
                "\\[\\]",
                true);

        // 4.5. Задачи между второй и третьей порцией
        String betweenFrom = afterFirstTasks.minusSeconds(1).format(FORMATTER);
        String betweenTo = afterSecondTasks.plusSeconds(4).format(FORMATTER);
        testFilter("4.5. Задачи между " + betweenFrom + " и " + betweenTo,
                BASE_URL + "?userId=555&from=" + betweenFrom + "&to=" + betweenTo,
                "Status: 200",
                ".*\"id\":17.*\"id\":18.*",
                true);
        testFilter("4.5.1. Проверка отсутствия задачи 19",
                BASE_URL + "?userId=555&from=" + betweenFrom + "&to=" + betweenTo,
                "Status: 200",
                ".*\"id\":19.*",
                false);

        // 4.6. Задачи с очень старыми датами
        String fromVeryOld = testStartTime.minusDays(365).format(FORMATTER);
        String toVeryFuture = finalTime.plusDays(365).format(FORMATTER);
        testFilter("4.6. Задачи с очень старыми датами (все 6 задач)",
                BASE_URL + "?userId=555&from=" + fromVeryOld + "&to=" + toVeryFuture,
                "Status: 200",
                ".*\"id\":14.*\"id\":15.*\"id\":16.*\"id\":17.*\"id\":18.*\"id\":19.*",
                true);

        // 4.7. Задачи с будущими датами
        String fromFuture = finalTime.plusMinutes(5).format(FORMATTER);
        testFilter("4.7. Задачи с будущими датами (0 задач)",
                BASE_URL + "?userId=555&from=" + fromFuture + "&to=" + toVeryFuture,
                "Status: 200",
                "\\[\\]",
                true);

        // 4.8. Только даты без userId
        testOperation("4.8. Только даты без userId (валидация)",
                BASE_URL + "?from=" + fromVeryOld + "&to=" + toVeryFuture,
                null,
                "GET",
                "Status: 400",
                null,
                true);
    }

    private static void testBasicOperations() {
        System.out.println("=== ОСНОВНЫЕ ОПЕРАЦИИ CRUD ===\n");

        // GET операции
        System.out.println("--- GET ЗАПРОСЫ ---");
        testGetTaskById("GET задача id=6", BASE_URL + "/6", 6, 777);
        testOperation("GET несуществующая задача id=55", BASE_URL + "/55", null, "GET",
                "Status: 404", null, true);

        // PUT операции
        System.out.println("\n--- PUT ЗАПРОСЫ ---");
        String updateJson = "{\"id\":7,\"title\":\"Обновленный заголовок\",\"status\":\"DONE\"}";
        testOperation("PUT обновление задачи id=5", BASE_URL + "/5", updateJson, "PUT",
                "Status: 200", null, true);
        testOperation("PUT обновление несуществующей задачи id=55", BASE_URL + "/55", updateJson, "PUT",
                "Status: 404", null, true);

        // DELETE операции
        System.out.println("\n--- DELETE ЗАПРОСЫ ---");
        testOperation("DELETE задача id=1", BASE_URL + "/1", null, "DELETE",
                "Status: 204", null, true);
        testOperation("DELETE несуществующая задача id=55", BASE_URL + "/55", null, "DELETE",
                "Status: 204", null, true);

        // GET количество активных задач
        System.out.println("\n--- КОЛИЧЕСТВО АКТИВНЫХ ЗАДАЧ ---");
        testOperation("Количество активных задач userId=888", BASE_URL + "/active/count?userId=888", null, "GET",
                "Status: 200", "4", true);
        testOperation("Количество активных задач userId=666", BASE_URL + "/active/count?userId=666", null, "GET",
                "Status: 200", "0", true);
        testOperation("Количество активных задач userId=777", BASE_URL + "/active/count?userId=777", null, "GET",
                "Status: 200", "2", true);
        testOperation("Количество активных задач userId=222", BASE_URL + "/active/count?userId=222", null, "GET",
                "Status: 200", "0", true);

        // Финальные проверки всех пользователей
        System.out.println("\n--- ФИНАЛЬНЫЕ ПРОВЕРКИ ПОЛЬЗОВАТЕЛЕЙ ---");
        testUserTasks("Все задачи пользователя 666", BASE_URL + "?userId=666", 666, Set.of(2, 3));
        testUserTasks("Все задачи пользователя 777", BASE_URL + "?userId=777", 777, Set.of(4, 5, 6));
        testUserTasks("Все задачи пользователя 888", BASE_URL + "?userId=888", 888, Set.of(7, 8, 9, 10));
        testUserTasks("Все задачи пользователя 999", BASE_URL + "?userId=999", 999, Set.of(11, 12, 13));
    }

    private static void testGetTaskById(String testName, String url, int expectedId, int expectedUserId) {
        String response = sendRequest("GET", url, null);
        boolean statusOk = response.contains("Status: 200");
        boolean idOk = false;
        boolean userIdOk = false;

        if (statusOk) {
            Pattern idPattern = Pattern.compile("\"id\":(\\d+)");
            Pattern userIdPattern = Pattern.compile("\"userId\":(\\d+)");

            Matcher idMatcher = idPattern.matcher(response);
            Matcher userIdMatcher = userIdPattern.matcher(response);

            if (idMatcher.find()) {
                int actualId = Integer.parseInt(idMatcher.group(1));
                idOk = (actualId == expectedId);
            }

            if (userIdMatcher.find()) {
                int actualUserId = Integer.parseInt(userIdMatcher.group(1));
                userIdOk = (actualUserId == expectedUserId);
            }
        }

        System.out.print("  " + testName + ": ");
        if (statusOk && idOk && userIdOk) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            if (!idOk)
                System.out.println("    Ожидался id=" + expectedId);
            if (!userIdOk)
                System.out.println("    Ожидался userId=" + expectedUserId);
            failedTests++;
        }
    }

    private static void testUserTasks(String testName, String url, int userId, Set<Integer> expectedIds) {
        String response = sendRequest("GET", url, null);
        boolean statusOk = response.contains("Status: 200");
        boolean idsOk = true;

        if (statusOk) {
            Set<Integer> actualIds = extractIdsFromResponse(response);
            idsOk = actualIds.equals(expectedIds);

            if (!idsOk) {
                System.out.println("    Ожидаемые ID: " + expectedIds);
                System.out.println("    Фактические ID: " + actualIds);
            }
        }

        System.out.print("  " + testName + ": ");
        if (statusOk && idsOk) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            failedTests++;
        }
    }

    private static Set<Integer> extractIdsFromResponse(String response) {
        Set<Integer> ids = new HashSet<>();
        Pattern pattern = Pattern.compile("\"id\":(\\d+)");
        Matcher matcher = pattern.matcher(response);

        while (matcher.find()) {
            ids.add(Integer.parseInt(matcher.group(1)));
        }

        return ids;
    }

    private static void testFilter(String testName, String url, String expectedStatus,
            String pattern, boolean shouldMatch) {
        String response = sendRequest("GET", url, null);
        boolean statusOk = response.contains(expectedStatus);
        boolean patternOk = true;

        if (pattern != null) {
            Pattern p = Pattern.compile(pattern, Pattern.DOTALL);
            Matcher m = p.matcher(response);
            patternOk = shouldMatch ? m.find() : !m.find();
        }

        boolean testPassed = statusOk && patternOk;

        System.out.print("  " + testName + ": ");
        if (testPassed) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            System.out.println("    Ожидаемый статус: " + expectedStatus);
            failedTests++;
        }
    }

    private static void testOperation(String testName, String url, String body, String method,
            String expectedStatus, String expectedContent, boolean shouldMatch) {
        String response = sendRequest(method, url, body);
        boolean statusOk = response.contains(expectedStatus);
        boolean contentOk = true;

        if (expectedContent != null) {
            contentOk = shouldMatch ? response.contains(expectedContent) : !response.contains(expectedContent);
        }

        boolean testPassed = statusOk && contentOk;

        System.out.print("  " + testName + ": ");
        if (testPassed) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            System.out.println("    Ожидаемый статус: " + expectedStatus);
            if (expectedContent != null) {
                System.out.println("    Ожидаемое содержимое: " + expectedContent);
            }
            failedTests++;
        }
    }

    private static void printSummary() {
        printHeader("ИТОГОВАЯ СТАТИСТИКА");

        System.out.println("┌──────────────────────────────────────────────┐");
        System.out.printf("│  Всего тестов: %-30d │\n", (passedTests + failedTests));
        System.out.printf("│  Пройдено:     %-30d │\n", passedTests);
        System.out.printf("│  Провалено:    %-30d │\n", failedTests);
        System.out.println("├──────────────────────────────────────────────┤");

        if (failedTests == 0) {
            System.out.println("│  РЕЗУЛЬТАТ: ВСЕ ТЕСТЫ УСПЕШНО ПРОЙДЕНЫ!      │");
        } else {
            System.out.printf("│  РЕЗУЛЬТАТ: ЕСТЬ ОШИБКИ (%d тестов не пройдено)│\n", failedTests);
        }
        System.out.println("└──────────────────────────────────────────────┘");

        System.out.println("\n" + LINE);
        System.out.println("  ТЕСТИРОВАНИЕ ЗАВЕРШЕНО");
        System.out.println("  Время окончания: " + LocalDateTime.now().format(FORMATTER));
        System.out.println(LINE);
    }

    public static void createUsers() {
        System.out.println("СОЗДАНИЕ ТЕСТОВЫХ ПОЛЬЗОВАТЕЛЕЙ И ЗАДАЧ");
        System.out.println(SUB_LINE);

        String[][] userTasks = {
                { "666", "Починить компьютер", "DONE" },
                { "666", "Заказать пиццу", "DONE" },
                { "666", "Выгулять собаку", "DONE" },
                { "777", "Починить компьютер", "IN_PROGRESS" },
                { "777", "Заказать пиццу", "DONE" },
                { "777", "Выгулять собаку", "OPEN" },
                { "888", "Прочитать книгу", "IN_PROGRESS" },
                { "888", "Подготовить презентацию", "OPEN" },
                { "888", "Сдать проект", "IN_PROGRESS" },
                { "888", "Купить продукты", "OPEN" },
                { "999", "Написать код", "IN_PROGRESS" },
                { "999", "Протестировать API", "OPEN" },
                { "999", "Закоммитить изменения", "DONE" }
        };

        for (String[] task : userTasks) {
            String userId = task[0];
            String title = task[1];
            String status = task[2];

            String json = String.format(
                    "{\"title\":\"%s\",\"createdBy\":%s,\"status\":\"%s\"}",
                    title, userId, status);

            sendRequest("POST", BASE_URL, json);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }
        System.out.println("Создание пользователей завершено\n");
    }

    public static void createFirstTasks() {
        System.out.println("Создание первой порции задач (3 задачи):");

        String[][] tasks = {
                { "Задача 1 - Починить компьютер", "IN_PROGRESS" },
                { "Задача 2 - Заказать пиццу", "DONE" },
                { "Задача 3 - Выгулять собаку", "OPEN" }
        };

        for (int i = 0; i < tasks.length; i++) {
            String json = String.format(
                    "{\"title\":\"%s\",\"createdBy\":555,\"status\":\"%s\"}",
                    tasks[i][0], tasks[i][1]);

            String response = sendRequest("POST", BASE_URL, json);
            if (response.contains("Status: 201")) {
                System.out.println("  Задача " + (i + 1) + ": [OK]");
            } else {
                System.out.println("  Задача " + (i + 1) + ": [ERROR]");
            }
        }
    }

    public static void createSecondTasks() {
        System.out.println("Создание второй порции задач (2 задачи):");

        String[][] tasks = {
                { "Задача 4 - Прочитать книгу (создана через 10 сек)", "IN_PROGRESS" },
                { "Задача 5 - Подготовить презентацию (создана через 10 сек)", "OPEN" }
        };

        for (int i = 0; i < tasks.length; i++) {
            String json = String.format(
                    "{\"title\":\"%s\",\"createdBy\":555,\"status\":\"%s\"}",
                    tasks[i][0], tasks[i][1]);

            String response = sendRequest("POST", BASE_URL, json);
            if (response.contains("Status: 201")) {
                System.out.println("  Задача " + (i + 4) + ": [OK]");
            } else {
                System.out.println("  Задача " + (i + 4) + ": [ERROR]");
            }
        }
    }

    public static void createThirdTask() {
        System.out.println("Создание третьей порции задач (1 задача):");

        String json = "{\"title\":\"Задача 6 - Сдать проект (создана через 15 сек)\",\"createdBy\":555,\"status\":\"IN_PROGRESS\"}";
        String response = sendRequest("POST", BASE_URL, json);

        if (response.contains("Status: 201")) {
            System.out.println("  Задача 6: [OK]");
        } else {
            System.out.println("  Задача 6: [ERROR]");
        }
    }

    private static String sendRequest(String method, String url, String body) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json");

            switch (method.toUpperCase()) {
                case "POST":
                    requestBuilder.POST(BodyPublishers.ofString(body));
                    break;
                case "PUT":
                    requestBuilder.PUT(BodyPublishers.ofString(body));
                    break;
                case "DELETE":
                    requestBuilder.DELETE();
                    break;
                case "GET":
                default:
                    requestBuilder.GET();
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return "Status: " + response.statusCode() + " | Body: " + response.body();

        } catch (Exception e) {
            return "Ошибка: " + e.getMessage();
        }
    }
}