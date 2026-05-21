import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


public class DatabaseHandler {
    private static final String DB_URL = "jdbc:sqlite:C:/Users/user/Desktop/UsersList.db";
    private Connection connection;

    public DatabaseHandler() {
        connect();
        createTablesIfNotExists();
    }

    private void connect() {
        try {
            // Загружаем драйвер
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(DB_URL);
            System.out.println("✓ Подключение к БД установлено");
        } catch (ClassNotFoundException e) {
            System.err.println("✗ Драйвер SQLite не найден! Добавьте sqlite-jdbc.jar в проект");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("✗ Ошибка подключения к БД: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTablesIfNotExists() {
        String createUsersTable = """
            CREATE TABLE IF NOT EXISTS UsersList (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                pass TEXT NOT NULL
            )
            """;

        String createMessagesTable = """
            CREATE TABLE IF NOT EXISTS MessageText (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender_id INTEGER NOT NULL,
                receiver_id INTEGER,
                text TEXT NOT NULL,
                sent_time TEXT NOT NULL,
                is_received INTEGER DEFAULT 0,
                FOREIGN KEY (sender_id) REFERENCES UsersList(id),
                FOREIGN KEY (receiver_id) REFERENCES UsersList(id)
            )
            """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createUsersTable);
            stmt.execute(createMessagesTable);
            System.out.println("✓ Таблицы проверены/созданы");
        } catch (SQLException e) {
            System.err.println("✗ Ошибка создания таблиц: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Регистрация пользователя
    public boolean registerUser(String nickname, String password) {
        String sql = "INSERT INTO UsersList (name, pass) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, nickname.toLowerCase());
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("✗ Ошибка регистрации: " + e.getMessage());
            return false;
        }
    }

    // Проверка существования пользователя
    public boolean userExists(String nickname) {
        String sql = "SELECT COUNT(*) FROM UsersList WHERE name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, nickname.toLowerCase());
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    // Вход пользователя (возвращает ID или null)
    public Integer loginUser(String nickname, String password) {
        String sql = "SELECT id FROM UsersList WHERE name = ? AND pass = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, nickname.toLowerCase());
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? rs.getInt("id") : null;
        } catch (SQLException e) {
            System.err.println("✗ Ошибка входа: " + e.getMessage());
            return null;
        }
    }

    // Получение ID пользователя по имени
    public Integer getUserIdByNickname(String nickname) {
        String sql = "SELECT id FROM UsersList WHERE name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, nickname.toLowerCase());
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? rs.getInt("id") : null;
        } catch (SQLException e) {
            return null;
        }
    }

    // Сохранение сообщения
    public void saveMessage(int senderId, Integer receiverId, String text) {
        String sql = "INSERT INTO MessageText (sender_id, receiver_id, text, sent_time, is_received) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, senderId);
            if (receiverId != null) {
                pstmt.setInt(2, receiverId);
            } else {
                pstmt.setNull(2, Types.INTEGER);
            }
            pstmt.setString(3, text);
            pstmt.setString(4, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            pstmt.setInt(5, 1);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("✗ Ошибка сохранения сообщения: " + e.getMessage());
        }
    }

    // Получение истории сообщений
    public List<MessageInfo> getChatHistory(int userId1, int userId2, int limit) {
        List<MessageInfo> messages = new ArrayList<>();
        String sql = """
            SELECT m.id, m.sender_id, m.text, m.sent_time, u.name as sender_name
            FROM MessageText m
            JOIN UsersList u ON m.sender_id = u.id
            WHERE (m.sender_id = ? AND m.receiver_id = ?) 
               OR (m.sender_id = ? AND m.receiver_id = ?)
               OR (m.receiver_id IS NULL)
            ORDER BY m.sent_time DESC
            LIMIT ?
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId1);
            pstmt.setInt(2, userId2);
            pstmt.setInt(3, userId2);
            pstmt.setInt(4, userId1);
            pstmt.setInt(5, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                messages.add(new MessageInfo(
                        rs.getInt("id"),
                        rs.getInt("sender_id"),
                        rs.getString("sender_name"),
                        rs.getString("text"),
                        rs.getString("sent_time")
                ));
            }
        } catch (SQLException e) {
            System.err.println("✗ Ошибка получения истории: " + e.getMessage());
        }
        return messages;
    }

    // Поиск сообщений
    public List<MessageInfo> searchMessages(int userId1, int userId2, String searchText) {
        List<MessageInfo> messages = new ArrayList<>();
        String sql = """
            SELECT m.id, m.sender_id, m.text, m.sent_time, u.name as sender_name
            FROM MessageText m
            JOIN UsersList u ON m.sender_id = u.id
            WHERE ((m.sender_id = ? AND m.receiver_id = ?) 
                OR (m.sender_id = ? AND m.receiver_id = ?))
                AND m.text LIKE ?
            ORDER BY m.sent_time DESC
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId1);
            pstmt.setInt(2, userId2);
            pstmt.setInt(3, userId2);
            pstmt.setInt(4, userId1);
            pstmt.setString(5, "%" + searchText + "%");
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                messages.add(new MessageInfo(
                        rs.getInt("id"),
                        rs.getInt("sender_id"),
                        rs.getString("sender_name"),
                        rs.getString("text"),
                        rs.getString("sent_time")
                ));
            }
        } catch (SQLException e) {
            System.err.println("✗ Ошибка поиска: " + e.getMessage());
        }
        return messages;
    }

    // Внутренний класс для хранения информации о сообщении
    public static class MessageInfo {
        public final int id;
        public final int senderId;
        public final String senderNickname;
        public final String text;
        public final String sentTime;

        public MessageInfo(int id, int senderId, String senderNickname, String text, String sentTime) {
            this.id = id;
            this.senderId = senderId;
            this.senderNickname = senderNickname;
            this.text = text;
            this.sentTime = sentTime;
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("✓ Подключение к БД закрыто");
            }
        } catch (SQLException e) {
            System.err.println("✗ Ошибка закрытия БД: " + e.getMessage());
        }
    }
}