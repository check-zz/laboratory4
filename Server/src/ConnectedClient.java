
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
        if (name == null){
            if (data.isBlank()){
                sendData(MessageType.ERROR
                        + ProtocolConstants.COMMAND_SEPARATOR
                        + "Вы не можете использовать данное имя");
                sendData(MessageType.REQUEST
                        + ProtocolConstants.COMMAND_SEPARATOR
                        + "Введите имя");
                return;
            }

            if (!isValidUsername(data)){
                sendData(MessageType.ERROR
                        + ProtocolConstants.COMMAND_SEPARATOR
                        + "Имя должно начинаться с буквы");
                sendData(MessageType.REQUEST
                        + ProtocolConstants.COMMAND_SEPARATOR
                        + "Введите имя");
                return;
            }

            if (isInUse(data)){
                sendData(MessageType.ERROR
                        + ProtocolConstants.COMMAND_SEPARATOR
                        + "Такое имя уже занято");
                sendData(MessageType.REQUEST
                        + ProtocolConstants.COMMAND_SEPARATOR
                        + "Введите имя");
                return;
            }
            name = data;
            sendForAll(MessageType.INFO, "Пользователь "+ name + " вошел в чат");
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