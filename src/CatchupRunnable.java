import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
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

        System.out.println(blockchain.getLength() + "," + remoteBlockchainLength);
        if(blockchain.getLength() < remoteBlockchainLength)
        {
            System.out.println("I know blockhain is smaller");
            // Ask for their blockchain from current hash
            blockchain = catchUp(blockchain, clientSocket, remotePort, remoteBlockchainHash);

        }
        else if (blockchain.getLength() == remoteBlockchainLength)
        {
            System.out.println("Blockchain is same size");
            // Check if hash is smaller
            byte[] remoteBlockchainHashByte = Base64.getDecoder().decode(remoteBlockchainHash);
            byte[] myHash = blockchain.getHead().calculateHash();

            if(hashIsSmaller(remoteBlockchainHashByte, myHash))
            {
                blockchain = catchUp(blockchain, clientSocket, remotePort, remoteBlockchainHash);
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

            ObjectInputStream ois = new ObjectInputStream(toServer.getInputStream());
            Block newBlock = (Block) ois.readObject();
            // Received block
            blockStack.push(newBlock);
            System.out.println("here 1");
            System.out.println("I've received the block \n" + newBlock);
            toServer.close();

            if(blockchain == null) System.out.println("Blockchain is null");
            if(newBlock == null) System.out.println("New Block is null");
            // Loop over this
            while (!blockchain.containsHash(newBlock.getPreviousHash()))
            {
                if(Arrays.equals(newBlock.getPreviousHash(), new byte[32]))
                    break;
                Socket newSocket = new Socket();
                newSocket.connect(new InetSocketAddress(remoteIP, remotePort), 2000);
                PrintWriter pw = new PrintWriter(newSocket.getOutputStream(), true);

                message = "cu|" + Base64.getEncoder().encodeToString(newBlock.getPreviousHash());
                pw.println(message);
                pw.flush();

                ObjectInputStream ois2 = new ObjectInputStream(newSocket.getInputStream());

                newBlock = (Block) ois2.readObject();
                blockStack.push(newBlock);

                ois2.close();
                pw.close();
                newSocket.close();
            }
            // The stack now contains all the blocks that I need to add
            // Now I have to discard some of my current blocks

            // Find the block which is the one where I can join

            byte[] hash = blockStack.peek().getPreviousHash();

            // Discard the rest of the blocks and add their transactions to a pool

            Block latestBlock = blockchain.getBlock(Base64.getEncoder().encodeToString(hash));


            prune(blockchain, latestBlock, blockchain.getPool());

            // Do the fork here
            Block currentBlock = latestBlock;


            while(!blockStack.isEmpty())
            {
                newBlock = blockStack.pop();
                // Change the localPool here
                blockchain.getPool().removeAll(newBlock.getTransactions());
                newBlock.setPreviousBlock(currentBlock);
                blockchain.setHead(newBlock);
                blockchain.setLength(blockchain.getLength() + 1);
                currentBlock = newBlock;
            }
            System.out.println(blockchain.getLength());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }


        return blockchain;

    }

    private static synchronized void changePool(ArrayList<Transaction> localPool, ArrayList<Transaction> transactions) {

        localPool.removeAll(transactions);

    }

    private static synchronized void prune(Blockchain blockchain, Block latestBlock, ArrayList<Transaction> localPool) {
        Block head = blockchain.getHead();
        while(head != latestBlock)
        {
            localPool.addAll(head.getTransactions());
            head = head.getPreviousBlock();
            blockchain.setLength(blockchain.getLength() - 1);
        }
    }

    private static boolean hashIsSmaller(byte[] remoteHash, byte[] localHash) {
        if(Arrays.equals(remoteHash, localHash))
            return false;
        for(int i = 0; i < remoteHash.length; ++i)
        {
            if(localHash[i] > remoteHash[i])
                return false;
        }

        return true;
    }
}
