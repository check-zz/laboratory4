package UI;

import java.awt.*;
import java.util.function.Consumer;

public interface UI {
    void showInfo(String data, TrayIcon.MessageType type);

    void addUserDataListener(Consumer<String> listener);

    void removeUserDataListener(Consumer<String> listener);
}
