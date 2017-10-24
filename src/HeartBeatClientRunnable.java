import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class HeartBeatClientRunnable implements Runnable{

    private ServerInfo serverInfo;
    private String message;

    public HeartBeatClientRunnable(ServerInfo serverInfo, String message) {
        this.serverInfo = serverInfo;
        this.message = message;
    }

    @Override
    public void run() {
        try {
            // create socket with a timeout of 2 seconds
            Socket toServer = new Socket();

            toServer.connect(new InetSocketAddress(serverInfo.getHost(), serverInfo.getPort()), 2000);

            String localIP = (((InetSocketAddress) toServer.getLocalSocketAddress()).getAddress()).toString().replace("/", "");
            int localPort = toServer.getLocalPort();

            if(new ServerInfo(localIP, localPort).equals(serverInfo))
                return;

            PrintWriter printWriter = new PrintWriter(toServer.getOutputStream(), true);

            // send the message forward
            printWriter.println(message);
            System.out.println("I'm sending a message: " + message);
            printWriter.flush();

            // close printWriter and socket
            printWriter.close();
            toServer.close();
        } catch (IOException e) {
        }
    }
}
