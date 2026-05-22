package UI;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;
import net.MessageType;
import net.ProtocolConstants;


 public class ConsoleUi implements UI{

    private final List<Consumer<String>> listeners = new ArrayList<>();

     private String myName = null;

     public void start(){
        var scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        new Thread(()-> {
            while (true) {
                var userData = scanner.nextLine();
                for (var listener : listeners) {
                    listener.accept(userData);
                }
            }
        }).start();
    }

    @Override
    public void showInfo(String data, MessageType type) {
        switch (type){
            case MESSAGE -> {
                var message = data.split(
                        ProtocolConstants.AUTHOR_SEPARATOR,
                        2
                );
                String author = (message[0]);
                String text = (message[1]);

                // ← НОВАЯ ЛОГИКА: проверяем, своё сообщение или чужое
                if (myName != null && myName.equalsIgnoreCase(author)) {
                    System.out.println("Вы: ");
                    System.out.println(text);
                } else {
                    System.out.println(author + ": ");
                    System.out.println(text);
                }
            }

            case INFO -> {
                // ← АВТОМАТИЧЕСКИ ИЗВЛЕКАЕМ ИМЯ ИЗ СООБЩЕНИЯ О ВХОДЕ
                if (data.startsWith("Пользователь ") && data.endsWith(" вошел в чат")) {
                    String extractedName = data.substring(
                            "Пользователь ".length(),
                            data.length() - " вошел в чат".length()
                    );
                    if (myName == null) {
                        myName = extractedName;
                        System.out.println("Ваше имя: " + myName);
                        System.out.println("Теперь ваши сообщения будут показываться как 'Вы написали:'");
                    }
                }
                System.out.println(data);
            }
            case ERROR -> {
                System.err.println(data);
            }
            default -> {
                System.out.println(data);
            }
        }
    }

    @Override
    public void addUserDataListener(Consumer<String> listener) {
        if (!listeners.contains(listener)) {  // ← предотвращаем дублирование
            listeners.add(listener);
        }
    }

    @Override
    public void removeUserDataListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

}