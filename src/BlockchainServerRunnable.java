import java.io.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

public class BlockchainServerRunnable implements Runnable {

    private Socket clientSocket;
    private Blockchain blockchain;
    private ConcurrentHashMap<ServerInfo, Date> serverStatus;

    public BlockchainServerRunnable(Socket clientSocket, Blockchain blockchain, ConcurrentHashMap<ServerInfo, Date> serverStatus) {
        this.clientSocket = clientSocket;
        this.blockchain = blockchain;
        this.serverStatus = serverStatus;
    }

    public void run() {
        try {
            serverHandler(clientSocket.getInputStream(), clientSocket.getOutputStream());
            clientSocket.close();
        } catch (IOException e) {
        }
    }

    public void serverHandler(InputStream clientInputStream, OutputStream clientOutputStream) {

        BufferedReader inputReader = new BufferedReader(
                new InputStreamReader(clientInputStream));
        PrintWriter outWriter = new PrintWriter(clientOutputStream, true);

        try {
            while (true) {
                String inputLine = inputReader.readLine();
                if (inputLine == null) {
                    break;
                }

                String[] tokens = inputLine.split("\\|");
                switch (tokens[0]) {
                    case "tx":
                        if (blockchain.addTransaction(inputLine))
                            outWriter.print("Accepted\n\n");
                        else
                            outWriter.print("Rejected\n\n");
                        outWriter.flush();
                        break;
                    case "pb":
                        outWriter.print(blockchain.toString() + "\n");
                        outWriter.flush();
                        break;
                    case "cc":
                        return;
                    case "hb":
                        //"hb|" + localPort + "|" + sequenceNumber
                        int localPort = clientSocket.getLocalPort();
                        int remotePort = Integer.parseInt(tokens[1]);

                        String localIP = (((InetSocketAddress) clientSocket.getLocalSocketAddress()).getAddress()).toString().replace("/", "");
                        String remoteIP = (((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getAddress()).toString().replace("/", "");

                        ServerInfo localServerInfo = new ServerInfo(localIP, localPort);
                        ServerInfo remoteServerInfo = new ServerInfo(remoteIP, remotePort);

                        if (remoteServerInfo.isValid()) {
                            serverStatus.put(remoteServerInfo, new Date());
                        }
                        if (tokens[2].equals("0")) {
                            ArrayList<Thread> threadArrayList = new ArrayList<>();
                            for (ServerInfo serverInfo : serverStatus.keySet()) {
                                if (serverInfo.equals(localServerInfo) || serverInfo.equals(remoteServerInfo)) {
                                    continue;
                                }
                                Thread thread = new Thread(new HeartBeatClientRunnable(serverInfo, "si|" + localPort + "|" + remoteIP + "|" + remotePort));
                                threadArrayList.add(thread);
                                thread.start();
                            }
                            for (Thread thread : threadArrayList) {
                                thread.join();
                            }
                        }
                        break;
                    case "si":
                        int senderPort = Integer.parseInt(tokens[1]);
                        String senderIP = (((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getAddress()).toString().replace("/", "");

                        ServerInfo senderServerInfo = new ServerInfo(senderIP, senderPort);

                        localIP = (((InetSocketAddress) clientSocket.getLocalSocketAddress()).getAddress()).toString().replace("/", "");
                        localPort = clientSocket.getLocalPort();

                        localServerInfo = new ServerInfo(localIP, localPort);

                        remoteIP = tokens[2];
                        remotePort = Integer.parseInt(tokens[3]);

                        remoteServerInfo = new ServerInfo(remoteIP, remotePort);

                        if (remoteServerInfo.isValid())
                            serverStatus.put(remoteServerInfo, new Date());

                        // relay
                        ArrayList<Thread> threadArrayList = new ArrayList<>();
                        for (ServerInfo serverInfo : serverStatus.keySet()) {
                            if (serverInfo.equals(localServerInfo) || serverInfo.equals(remoteServerInfo) || serverInfo.equals(senderServerInfo)) {
                                continue;
                            }
                            Thread thread = new Thread(new HeartBeatClientRunnable(serverInfo, "si|" + localPort + "|" + remoteIP + "|" + remotePort));
                            threadArrayList.add(thread);
                            thread.start();
                        }
                        for (Thread thread : threadArrayList) {
                            thread.join();
                        }
                        break;
                    case "lb":
                        //"lb|" + localPort + "|" + blockchain.getLength() + "|" + Base64.getEncoder().encodeToString(blockchain.getHead().calculateHash()))
                        // If my blockchain length is smaller than the one I received, I ask for their blockchain by sending cu
                        //Thread thread = new Thread(new CatchupRunnable(blockchain, clientSocket, inputLine));

                        break;
                    case "cu":
                        // I send information about my blockchain after reading the
                        ObjectOutputStream oos = new ObjectOutputStream(clientOutputStream);
                        if(tokens.length == 1) {

                            oos.writeObject(blockchain.getHead());
                            oos.flush();
                            break;
                        }
                        else {
                            String remoteBlockchainHash = tokens[1];
                            oos.writeObject(blockchain.getBlock(remoteBlockchainHash));
                            oos.flush();
                            break;
                        }
                    default:
                        outWriter.print("Error\n\n");
                        outWriter.flush();
                }
            }
        } catch (IOException e) {
        } catch (InterruptedException e) {
        }
    }


}
