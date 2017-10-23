import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class PeriodicHeartBeatRunnable implements Runnable {

    private ConcurrentHashMap<ServerInfo, Date> serverStatus;
    private int sequenceNumber;
    private int localPort;

    public PeriodicHeartBeatRunnable(ConcurrentHashMap<ServerInfo, Date> serverStatus,
                                     int localPort) {
        this.serverStatus = serverStatus;
        this.sequenceNumber = 0;
        this.localPort = localPort;
    }

    @Override
    public void run() {
        while(true) {

            ArrayList<Thread> threadArrayList = new ArrayList<>();
            for (ServerInfo serverInfo : serverStatus.keySet()) {
                Thread thread = new Thread(new HeartBeatClientRunnable(serverInfo, "hb|" + localPort + "|" + sequenceNumber));
                threadArrayList.add(thread);
                thread.start();
            }

            for (Thread thread : threadArrayList) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                }
            }

            // increment the sequenceNumber
            sequenceNumber += 1;

            // sleep for two seconds
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }
        }
    }
}
