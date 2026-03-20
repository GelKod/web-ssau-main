import java.io.IOException;

public class TruncateTableSimple {
    public static void main(String[] args) {
        // Твои данные
        String dbName = "taskdb";
        String dbUser = "gelkod";
        String dbPassword = "gelkod"; // ← лучше не хардкодить в реальной жизни
        String tableName = "task";

        // Команда, которую мы хотим выполнить
        String sql = "TRUNCATE TABLE " + tableName + " RESTART IDENTITY CASCADE;";

        // Формируем команду для psql
        // -c = выполнить команду и выйти
        // -d = имя базы
        // -U = пользователь
        ProcessBuilder pb = new ProcessBuilder(
                "psql",
                "-d", dbName,
                "-U", dbUser,
                "-c", sql);

        // Передаём пароль через переменную окружения (самый простой безопасный способ)
        pb.environment().put("PGPASSWORD", dbPassword);

        try {
            Process process = pb.start();

            // Ждём завершения
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("Таблица '" + tableName + "' успешно очищена через psql.");
            } else {
                System.err.println("Ошибка выполнения psql. Код возврата: " + exitCode);
                // Можно вывести ошибки, если хочешь
                // java.io.InputStream errorStream = process.getErrorStream();
            }

        } catch (IOException e) {
            System.err.println("Не удалось запустить psql: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Процесс прерван");
        }
    }
}