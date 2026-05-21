import UI.ClientGui;
import javax.swing.SwingUtilities;

public class Main {
        public static void main(String[] args) {
                SwingUtilities.invokeLater(() -> {
                        ClientGui gui = new ClientGui();
                        gui.addUserDataListener(data -> {
                                if (gui.client != null) {
                                        gui.client.sendData(data);
                                }
                        });
                });
        }
}