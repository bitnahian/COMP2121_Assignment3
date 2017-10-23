import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PeriodicConsensusRunnable implements Runnable{

    ConcurrentHashMap<ServerInfo, Date> serverStatus;
    private Blockchain blockchain;
    private int localPort;

    public PeriodicConsensusRunnable(ConcurrentHashMap<ServerInfo, Date> serverStatus, Blockchain blockchain, int localPort) {
        this.serverStatus = serverStatus;
        this.blockchain = blockchain;
        this.localPort = localPort;
    }

    @Override
    public void run() {

        while(true) {

            ArrayList<Thread> threadArrayList = new ArrayList<>();

            ArrayList<ServerInfo> list = new ArrayList<>(serverStatus.keySet());
            Collections.shuffle(list);
            int i = 0;

            for (ServerInfo serverInfo : list) {
                if(i > 4) break;
                Thread thread = new Thread(new HeartBeatClientRunnable(serverInfo, "lb|" + localPort + "|" + blockchain.getLength() + "|"
                        + Base64.getEncoder().encodeToString(blockchain.getHead().calculateHash())));
                threadArrayList.add(thread);
                thread.start();
                ++i;
            }

            for (Thread thread : threadArrayList) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                }
            }

            // sleep for two seconds
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }
        }
    }
}


