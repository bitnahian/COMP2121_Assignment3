import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BlockchainServer {

    public static void main(String[] args) {

        if (args.length != 3) {
            return;
        }

        int localPort = 0;
        int remotePort = 0;
        String remoteHost = null;

        try {
            localPort = Integer.parseInt(args[0]);
            remoteHost = args[1];
            remotePort = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println("Number Format Exception. Input was not a number.");
            return;
        }


        ConcurrentHashMap<ServerInfo, Date> serverStatus = new ConcurrentHashMap<ServerInfo, Date>();
        serverStatus.put(new ServerInfo(remoteHost, remotePort), new Date());

        Blockchain blockchain = initialCatchup(remoteHost, remotePort);

        PeriodicCommitRunnable pcr = new PeriodicCommitRunnable(blockchain);
        Thread pct = new Thread(pcr);
        pct.start();

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(localPort);
            // start up PeriodicChecker
            new Thread(new PeriodicServerInfoCheckerRunnable(serverStatus)).start();

            // start up PeriodicHeartBeat
            new Thread(new PeriodicHeartBeatRunnable(serverStatus, localPort)).start();

            // start up PeriodicConsensusRunnable
            //new Thread(new PeriodicConsensusRunnable(serverStatus, blockchain, localPort)).start();


            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("I've accepted a connection");
                new Thread(new BlockchainServerRunnable(clientSocket, blockchain, serverStatus)).start();
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Illegal Argument Exception. Invalid local port.");
        } catch (IOException e) {
        } finally {
            try {
                pcr.setRunning(false);
                pct.join();
                if (serverSocket != null)
                    serverSocket.close();
            } catch (IOException e) {
                System.err.println("IOException occurred. Aborting.");
            } catch (InterruptedException e) {
                System.err.println("Thread was interrupted. Aborting.");
            }
        }
    }

    private static Blockchain initialCatchup(String remoteHost, int remotePort) {

        Blockchain blockchain = new Blockchain();
        try {
            // create socket with a timeout of 2 seconds
            Socket toServer = new Socket();
            toServer.connect(new InetSocketAddress(remoteHost, remotePort), 2000);
            PrintWriter printWriter = new PrintWriter(toServer.getOutputStream(), true);
            //Stack<Block> blockStack = new Stack();

            // send the message forward
            String message = "cu";
            printWriter.println(message);
            printWriter.flush();

            ObjectInputStream ois = new ObjectInputStream(toServer.getInputStream());
            Block newBlock = (Block) ois.readObject();

            if(newBlock == null) return blockchain; // This means the other blockchain is also empty

            //blockStack.push(newBlock);
            blockchain.setHead(newBlock);
            // Loop over this
            toServer.close();

            int length = 1;
            Block currentBlock = newBlock;

            while (!Arrays.equals(newBlock.getPreviousHash(), new byte[32]))
            {
                Socket newSocket = new Socket();
                newSocket.connect(new InetSocketAddress(remoteHost, remotePort), 2000);
                PrintWriter pw = new PrintWriter(newSocket.getOutputStream(), true);

                message = "cu|" + Base64.getEncoder().encodeToString(newBlock.getPreviousHash());
                pw.println(message);
                pw.flush();

                ObjectInputStream ois2 = new ObjectInputStream(newSocket.getInputStream());

                newBlock = (Block) ois2.readObject();
                currentBlock.setPreviousBlock(newBlock);
                currentBlock = newBlock;
                //blockStack.push(newBlock);
                length++;

                newSocket.close();
            }

            blockchain.setLength(length);

/*
            Block currentBlock = null;

            while(!blockStack.isEmpty())
            {
                currentBlock = blockchain.getHead();
                newBlock = blockStack.pop();
                newBlock.setPreviousBlock(currentBlock);
                blockchain.setHead(newBlock);
                blockchain.setLength(blockchain.getLength() + 1);
            }*/

            System.out.println(blockchain.toString());

            // close printWriter and socket
        } catch (IOException e) {
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return blockchain;
    }
}
