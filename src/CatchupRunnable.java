import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Base64;
import java.util.Stack;

public class CatchupRunnable implements Runnable{

    private Blockchain blockchain;
    private Socket clientSocket;
    String message;

    public CatchupRunnable(Blockchain blockchain, Socket clientSocket, String message) {

        this.blockchain = blockchain;
        this.clientSocket = clientSocket;
        this.message = message;
    }
    @Override
    public void run() {
        String[] tokens = message.split("\\|");
        int remotePort = Integer.parseInt(tokens[1]);
        int remoteBlockchainLength = Integer.parseInt(tokens[2]);
        String remoteBlockchainHash = tokens[3];

        if(blockchain.getLength() < remoteBlockchainLength)
        {
            // Ask for their blockchain from current hash
            if(remoteBlockchainLength - blockchain.getLength() == 1)
            {
                try {
                    // create socket with a timeout of 2 seconds
                    Socket toServer = new Socket();
                    String remoteIP = (((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getAddress()).toString().replace("/", "");
                    toServer.connect(new InetSocketAddress(remoteIP, remotePort), 2000);

                    PrintWriter printWriter = new PrintWriter(toServer.getOutputStream(), true);

                    // send the message forward
                    String message = "cu";
                    printWriter.println(message);
                    printWriter.flush();

                    // close printWriter and socket
                    printWriter.close();
                    toServer.close();

                    Thread.sleep(500);

                    ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream());

                    Block newBlock = (Block) ois.readObject();
                    newBlock.setPreviousBlock(blockchain.getHead());
                    blockchain.setHead(newBlock);
                    blockchain.setLength(blockchain.getLength() + 1);

                    return;

                } catch (IOException e) {
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }

            blockchain = catchUp(blockchain, clientSocket, remotePort, remoteBlockchainHash);

        }
        else if (blockchain.getLength() == remoteBlockchainLength)
        {
            // Check if hash is smaller
            byte[] remoteBlockchainHashByte = Base64.getDecoder().decode(remoteBlockchainHash);
            byte[] myHash = blockchain.getHead().calculateHash();

            if(hashIsSmaller(remoteBlockchainHashByte, myHash))
            {
                // Discard my blockchain
                blockchain = catchUp(new Blockchain(), clientSocket, remotePort, remoteBlockchainHash);
            }
        }
    }


    private synchronized static Blockchain catchUp(Blockchain blockchain, Socket clientSocket, int remotePort, String remoteBlockchainHash)
    {
        try {
            // create socket with a timeout of 2 seconds

            Socket toServer = new Socket();
            String remoteIP = (((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getAddress()).toString().replace("/", "");

            toServer.connect(new InetSocketAddress(remoteIP, remotePort), 2000);
            PrintWriter printWriter = new PrintWriter(toServer.getOutputStream(), true);
            Stack<Block> blockStack = new Stack();

            // send the message forward
            String message = "cu|" + remoteBlockchainHash;
            printWriter.println(message);
            printWriter.flush();

            Thread.sleep(500);

            ObjectInputStream ois = new ObjectInputStream(toServer.getInputStream());
            Block newBlock = (Block) ois.readObject();
            blockStack.push(newBlock);

            // Loop over this
            while (!Arrays.equals(newBlock.getPreviousHash(), new byte[32]))
            {
                message = "cu|" + Base64.getEncoder().encodeToString(newBlock.getPreviousHash());
                printWriter.println(message);
                printWriter.flush();

                Thread.sleep(500);

                newBlock = (Block) ois.readObject();
                blockStack.push(newBlock);
            }

            Block currentBlock = blockchain.getHead();

            while(!blockStack.isEmpty())
            {
                newBlock = blockStack.pop();
                currentBlock.setPreviousBlock(newBlock);
                blockchain.setHead(newBlock);
                blockchain.setLength(blockchain.getLength() + 1);
            }

            printWriter.close();
            toServer.close();
        } catch (IOException e) {
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return blockchain;

    }

    private static boolean hashIsSmaller(byte[] bi, byte[] bj) {
        for(int i = 0; i < bi.length; ++i)
        {
            if(bi[i] > bj[i])
                return false;
        }

        return true;
    }
}
