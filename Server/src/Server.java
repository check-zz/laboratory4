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
        try
        {
            try (var serverSocket = new ServerSocket(port))
            {
                System.out.println("Сервер запущен");
                while (isActive)
                {
                    try (var socket = serverSocket.accept();) {
                        //прием на сервер, отправка на клиенте, и наоборот
                        var br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        var string = br.readLine();
                        System.out.println("Клиент прислал: " + string);
                        var pw = new PrintWriter(socket.getOutputStream());
                        pw.println("Сервер принял ваше сообщение: " + string);
                        pw.flush();
                    }
                }
            }
        }
        catch (IOException e)
        {
            System.out.println("Ошибка включения сервера");
        }
    }
}
