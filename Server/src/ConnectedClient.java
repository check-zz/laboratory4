import java.io.BufferedReader;import java.io.InputStreamReader;import java.io.PrintStream;import java.io.PrintWriter;import java.net.Socket;

public class ConnectedClient
{
    private Socket socket;
    public boolean isActive = false;

    public ConnectedClient(Socket socket)
    {
     this.socket = socket;
    }

    public void start()
    {
        isActive = true;
        new Thread(()->{
           try ( var br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
               while (isActive)
               {
                   var data = br.readLine();
                   parseData(data);
               }
           }
           catch (Exception e)
           {
               System.out.println(" Ошибка чтения данных");
               isActive=false;
           }
     }).start();
    }

    public void stop()
    {
        isActive = false;
    }

    public void sendData(String data)
    {
        try(var pw = new PrintWriter(socket.getOutputStream()))
        {
            pw.println(data);
            pw.flush();
        }
        catch (Exception e)
        {
            System.out.println("Ошибка отправки данных");
            isActive = false;
        }
    }

    private void parseData(String data)
    {
        System.out.println("Клиент прислал: "+ data);
    }
}
