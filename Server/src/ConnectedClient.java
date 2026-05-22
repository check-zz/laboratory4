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
    private int userId = -1;
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
                        if (isInUse(username)) {
                            sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR
                                    + "Имя уже занято");
                            return;
                        }

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

            // === Обработка личных сообщений ===
            if (data.startsWith("@")) {
                int colonIndex = data.indexOf(":");
                if (colonIndex > 0) {
                    String receiverName = data.substring(1, colonIndex);
                    String messageText = data.substring(colonIndex + 1);

                    System.out.println("Личное сообщение от " + name + " для " + receiverName + ": " + messageText);

                    // Находим получателя
                    ConnectedClient receiver = findClientByName(receiverName);
                    if (receiver != null && receiver.authenticated) {
                        // Сохраняем в БД с указанием получателя
                        Integer receiverId = dbHandler.getUserIdByNickname(receiverName);
                        dbHandler.saveMessage(userId, receiverId, "[Личное] [" + receiverName + "] " + messageText);

                        System.out.println("→ Отправка получателю " + receiver.name);

                        // Отправляем только получателю
                        receiver.sendData(MessageType.MESSAGE + ProtocolConstants.COMMAND_SEPARATOR +
                                name + ProtocolConstants.AUTHOR_SEPARATOR + "[Личное] " + messageText);

                        // Отправляем себе подтверждение (чтобы видеть в своём чате)
                        sendData(MessageType.MESSAGE + ProtocolConstants.COMMAND_SEPARATOR +
                                name + ProtocolConstants.AUTHOR_SEPARATOR + "[Личное] [" + receiverName + "] " + messageText);

                        System.out.println("✓ Сообщение отправлено");
                    } else {
                        System.out.println("✗ Получатель не найден или не авторизован");
                        sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR +
                                "Пользователь " + receiverName + " не в сети");
                    }
                    return;  // Завершаем обработку, не рассылаем всем
                }
            }


            dbHandler.saveMessage(userId, null, data);

            sendForAll(MessageType.MESSAGE, data);
        }
    }

    // === НОВЫЙ МЕТОД: Поиск клиента по имени ===
    private ConnectedClient findClientByName(String name) {
        synchronized (clients) {
            for (ConnectedClient client : clients) {
                if (client.authenticated && client.name != null &&
                        client.name.equalsIgnoreCase(name)) {
                    System.out.println("✓ Найден получатель: " + name);
                    return client;
                }
            }
        }
        System.out.println("✗ Получатель не найден: " + name);
        return null;
    }


    private void sendForAll(MessageType type, String data){
        var author = (type == MessageType.MESSAGE) ?
                name + ProtocolConstants.AUTHOR_SEPARATOR :
                "";
        boolean anyoneReceived = false;

        synchronized (clients) {
            for (ConnectedClient client : clients) {
                // Не отправляем сообщение самому себе (оно уже есть у отправителя)
                if (client != this && client.authenticated) {
                    client.sendData(type + ProtocolConstants.COMMAND_SEPARATOR + author + data);
                    anyoneReceived = true;
                }
            }
        }

        if (type == MessageType.MESSAGE && anyoneReceived) {
            sendData(MessageType.READ_ACK + ProtocolConstants.COMMAND_SEPARATOR + "ACK");
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

        sendUserListToAll();

        // Сбрасываем данные
        name = null;
        userId = -1;
        authenticated = false;
    }

    private void sendUserListToAll() {
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
            String listData = MessageType.USER_LIST + ProtocolConstants.COMMAND_SEPARATOR + userList.toString();
            synchronized (clients) {
                for (ConnectedClient client : clients) {
                    if (client.authenticated) {
                        client.sendData(listData);
                    }
                }
            }
        }
    }

}