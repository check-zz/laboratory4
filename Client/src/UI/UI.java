package UI;

import java.awt.*;
import java.util.function.Consumer;
import net.MessageType;

public interface UI {
    void showInfo(String data, MessageType type);

    void addUserDataListener(Consumer<String> listener);

    void removeUserDataListener(Consumer<String> listener);
}
