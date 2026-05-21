
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
    private String name = null;

    private boolean authenticated = false;
    private String pendingName = null;


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
    //пункт 2
    private boolean isValidUsername(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        char firstChar = name.charAt(0);
        return Character.isLetter(firstChar);
    }

    private void parseData(String data){
        if (!authenticated) {
            // Ожидаем команду LOGIN или REGISTER
            if (data.startsWith("LOGIN:") || data.startsWith("REGISTER:")) {
                String[] parts = data.split(":", 3);
                if (parts.length == 3) {
                    String action = parts[0];
                    String username = parts[1];
                    String password = parts[2];

                    if (action.equals("REGISTER")) {
                        // Проверяем, что имя начинается с буквы
                        if (!Character.isLetter(username.charAt(0))) {
                            sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR
                                    + "Имя должно начинаться с буквы");
                            return;
                        }
                        // TODO: сохранить в БД
                        sendData(MessageType.INFO + ProtocolConstants.COMMAND_SEPARATOR
                                + "Регистрация успешна! Теперь войдите.");
                    }
                    else if (action.equals("LOGIN")) {
                        // Проверяем, что имя начинается с буквы
                        if (!Character.isLetter(username.charAt(0))) {
                            sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR
                                    + "Имя должно начинаться с буквы");
                            return;
                        }
                        // Проверяем, не занято ли имя
                        if (isInUse(username)) {
                            sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR
                                    + "Имя уже занято");
                            return;
                        }
                        // Успешный вход
                        name = username;
                        authenticated = true;
                        sendData(MessageType.INFO + ProtocolConstants.COMMAND_SEPARATOR
                                + "Добро пожаловать, " + name + "!");
                        sendForAll(MessageType.INFO, "Пользователь " + name + " вошел в чат");
                    }
                }
            } else {
                sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR
                        + "Сначала авторизуйтесь");
            }
        } else {
            sendForAll(MessageType.MESSAGE, data);
        }
    }

    private void sendForAll(MessageType type, String data){
        var author = (type == MessageType.MESSAGE) ?
                name + ProtocolConstants.AUTHOR_SEPARATOR :
                "";
        synchronized (clients) {
            clients.stream()
                    .filter(c -> c.name != null)
                    .forEach(client -> {
                        client.sendData(type
                                + ProtocolConstants.COMMAND_SEPARATOR
                                + author
                                + data);
                    });
        }
    }
    private boolean isInUse(String name){
        synchronized (clients) {
            return clients.stream()
                    .anyMatch(c -> c.name != null && c.name.equalsIgnoreCase(name));
        }
    }

    public void stop(){
        communicator.stop();
    }
}