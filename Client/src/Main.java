
import net.Client;
import UI.ConsoleUi;

void main() {
        try {
                var c = new Client("localhost", 8435);
                var ui = new ConsoleUi();
                ui.addUserDataListener(c::sendData);
                c.addDataListener(ui::showInfo);
                c.start();
                ui.start();
        } catch (IOException e) {
                System.out.println(e.getMessage());
        }
}