import javax.imageio.plugins.tiff.TIFFImageReadParam;
import java.io.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;

public class Server
{

    private boolean isActive;
    public Server(int port)
    {
        isActive = true;

            new Thread(()->
            {
                try (var serverSocket = new ServerSocket(port))
                {
                    System.out.println("Сервер запущен");
                    while (isActive)
                    {
                        try (var socket = serverSocket.accept();) {
                            System.out.println("Клиент подключен");
                            //прием на сервер, отправка на клиенте, и наоборот
                            var connClient = new ConnectedClient(socket);
                            connClient.start();
                        } catch (Exception e) {
                            System.out.println("Ошибка получения клиентов...");
                            isActive = false;
                        }
                    }
                }
                catch (IOException e)
                {
                    System.out.println("Ошибка включения сервера");
                }
            }).start();
    }
}
