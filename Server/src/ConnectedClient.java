import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import net.MessageType;
import net.Communicator;
import net.ProtocolConstants;

public class ConnectedClient {
    private final Communicator communicator;
    private final static List<ConnectedClient> clients = new ArrayList<>();
    private static DatabaseHandler dbHandler;

    private String name = null;
    private int userId = -1;  // <-- ДОБАВЛЕНО
    private boolean authenticated = false;

    static {
        dbHandler = new DatabaseHandler();
    }

    public ConnectedClient(Socket socket) throws IOException {
        communicator = new Communicator(socket);
        communicator.addDataListener(this::parseData);
        synchronized (clients) {
            clients.add(this);
        }
    }

    public void start(){
        communicator.start();
        sendData(MessageType.REQUEST
                + ProtocolConstants.COMMAND_SEPARATOR
                + "Введите имя:");
    }

    public void sendData(String data){
        communicator.sendData(data);
    }

    private boolean isValidUsername(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        char firstChar = name.charAt(0);
        return Character.isLetter(firstChar);
    }

    private void parseData(String data){
        if (!authenticated) {
            if (data.startsWith("LOGIN:") || data.startsWith("REGISTER:")) {
                String[] parts = data.split(":", 3);
                if (parts.length == 3) {
                    String action = parts[0];
                    String username = parts[1];
                    String password = parts[2];

                    if (!isValidUsername(username)) {
                        sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR
                                + "Имя должно начинаться с буквы");
                        return;
                    }

                    if (action.equals("REGISTER")) {
                        // ПРОВЕРКА: существует ли пользователь
                        if (dbHandler.userExists(username)) {
                            sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR
                                    + "Пользователь уже существует");
                            return;
                        }

                        if (dbHandler.registerUser(username, password)) {
                            sendData(MessageType.INFO + ProtocolConstants.COMMAND_SEPARATOR
                                    + "Регистрация успешна! Теперь войдите.");
                        } else {
                            sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR
                                    + "Ошибка регистрации");
                        }
                    }
                    else if (action.equals("LOGIN")) {
                        // Проверка, не занято ли имя (онлайн)
                        if (isInUse(username)) {
                            sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR
                                    + "Имя уже занято");
                            return;
                        }

                        // ПРОВЕРКА пароля и получение ID
                        Integer id = dbHandler.loginUser(username, password);
                        if (id != null) {
                            name = username;
                            userId = id;
                            authenticated = true;

                            sendData(MessageType.INFO + ProtocolConstants.COMMAND_SEPARATOR +
                                    "MY_NAME:" + name);

                            sendData(MessageType.INFO + ProtocolConstants.COMMAND_SEPARATOR
                                    + "Добро пожаловать, " + name + "!");

                            sendChatHistory();
                            sendUserList();
                            sendForAll(MessageType.INFO, "Пользователь " + name + " вошел в чат");
                        } else {
                            sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR
                                    + "Неверное имя или пароль");
                        }
                    }
                }
            } else {
                sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR
                        + "Сначала авторизуйтесь");
            }
        } else {

                if (data.startsWith("LOGOUT:")) {
                    stop(); // Корректно завершаем сессию
                    return;
                }


            dbHandler.saveMessage(userId, null, data);

            sendForAll(MessageType.MESSAGE, data);
        }
    }

    private void sendForAll(MessageType type, String data){
        var author = (type == MessageType.MESSAGE) ?
                name + ProtocolConstants.AUTHOR_SEPARATOR :
                "";
        synchronized (clients) {
            clients.stream()
                    .filter(c -> c.authenticated)  // <-- ИСПРАВЛЕНО: проверяем authenticated
                    .forEach(client -> {
                        client.sendData(type
                                + ProtocolConstants.COMMAND_SEPARATOR
                                + author
                                + data);
                    });
        }
    }

    // === НОВЫЙ МЕТОД: Отправка истории сообщений ===
    private void sendChatHistory() {
        List<DatabaseHandler.MessageInfo> history = dbHandler.getChatHistory(userId, -1, 50);
        for (DatabaseHandler.MessageInfo msg : history) {
            String formattedMsg = msg.senderNickname + ":" + msg.text;
            sendData(MessageType.HISTORY + ProtocolConstants.COMMAND_SEPARATOR + formattedMsg);
        }
    }

    // === НОВЫЙ МЕТОД: Отправка списка пользователей ===
    private void sendUserList() {
        StringBuilder userList = new StringBuilder();
        synchronized (clients) {
            for (ConnectedClient client : clients) {
                if (client.authenticated && client.name != null) {
                    if (userList.length() > 0) {
                        userList.append(",");
                    }
                    userList.append(client.name);
                }
            }
        }
        if (userList.length() > 0) {
            sendData(MessageType.USER_LIST + ProtocolConstants.COMMAND_SEPARATOR + userList.toString());
        }
    }



    private boolean isInUse(String name){
        synchronized (clients) {
            return clients.stream()
                    .anyMatch(c -> c.authenticated && c.name != null && c.name.equalsIgnoreCase(name));
        }
    }

    public void stop(){
        if (name != null && authenticated) {
            // Уведомляем ВСЕХ о выходе (включая самого себя)
            sendForAll(MessageType.INFO, "Пользователь " + name + " вышел из чата");
        }

        communicator.stop();

        synchronized (clients) {
            clients.remove(this); // Удаляем из списка
        }

        // Сбрасываем данные
        name = null;
        userId = -1;
        authenticated = false;
    }
}