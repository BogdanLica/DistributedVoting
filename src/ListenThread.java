import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ListenThread implements Runnable {
    private int timeout;
    private AtomicBoolean ready = new AtomicBoolean(false);
    private AtomicBoolean abort = new AtomicBoolean(false);
    private AtomicBoolean sendNewVotes = new AtomicBoolean(false);
    private int maxClients;
    private int failCond;
    private Queue<PeerReadThread> readers = new ConcurrentLinkedQueue<>();

    Map<Long,String> votes = new ConcurrentHashMap<>();
    private Map<Long,String> lastState;
    private AtomicInteger maxClientsInitially = new AtomicInteger(0);
    private ExecutorService ex;
    private Map<PeerReadThread,MessageToken.VoteToken> buffer = new ConcurrentHashMap<>();
    private Timer time = new Timer();
    private TimerTask task;

    /**
     * The Manager for all the read threads
     * @param timeout the max time to wait for a reader
     * @param maxConnections number of connections to monitor
     * @param fail the time of fail that might occur
     */
    public ListenThread(int timeout,int maxConnections,int fail) {
        this.timeout=timeout;
        maxClients=maxConnections;
        failCond=fail;

        task = new TimerTask() {
            public void run() {
                if(buffer.size() > 0){
                    recheckOnParticipants();

                }
            }
        };


    }

    /**
     * Move the votes received from the buffer to the main HashMap and notify of a new round
     */
    private void recheckOnParticipants() {
        moveVotesFromBuffer();
        sendNewVotes.set(true);

        if(lastState.equals(votes)){
            ready.set(true);
        }
    }

    /**
     * Copy the votes from the buffer to the table with all the votes
     */
    private void moveVotesFromBuffer() {
        Map<Long,String> votesThisRound = new HashMap<>();

        buffer.values().forEach(vote -> {
            List<Long> ports = vote.get_ports();
            List<String> outcomes = vote.get_outcome();

            Map<Long, String> map = IntStream.range(0, ports.size())
                    .boxed()
                    .collect(Collectors.toMap(ports::get, outcomes::get));

            votesThisRound.putAll(map);

        });

        lastState = new ConcurrentHashMap<>(votes);
        votes.putAll(votesThisRound);
    }


    /**
     * New reader to be created for the given socket
     * @param client the socket of a new client
     */
    public void addClient(Socket client){
        PeerReadThread reader = new PeerReadThread(client,timeout);
        readers.offer(reader);

//        PeerWriteThread writer = new PeerWriteThread(client);
//        writers.offer(writer);

//        new Thread(reader).start();
//        new Thread(writer).start();
    }



    @Override
    public void run() {


        maxClientsInitially.set(readers.size());
        ex = Executors.newFixedThreadPool(readers.size());
        readers.forEach( reader -> ex.submit(reader));



        time.schedule(task,timeout);


        while (!abort.get()){
            if(!ready.get()){

                String message;

                // enough votes
                if(votes.keySet().size() >= maxClientsInitially.get()){
                    ready.set(true);
                }

                // if no new votes yet, then keep listening for new votes
                if(!sendNewVotes.get()){
                        Iterator<PeerReadThread> readerIterator = readers.iterator();

                        while (readerIterator.hasNext())
                        {
                            PeerReadThread nextReader = readerIterator.next();

                            if(nextReader.isReady()){


                                buffer.put(nextReader,nextReader.getToken());
                                if(failCond == 1){
                                    sendNewVotes.set(true);
                                }
                                time.cancel();
                                time = new Timer();
                                TimerTask tmp = new TimerTask() {
                                    public void run() {
                                        if(buffer.size() > 0){
                                            moveVotesFromBuffer();

                                            if(lastState.equals(votes)){
                                                ready.set(true);
                                            }
                                            sendNewVotes.set(true);

                                        }
                                    }
                                };
                                time.schedule(tmp,timeout);
                            }

                        }


                    }

            }

        }

    }

    /**
     * Pause the monitoring
     */
    public void stopListening(){
        this.ready.set(true);
        this.sendNewVotes.set(true);
        time.cancel();
        buffer.clear();
        votes.clear();

    }

    /**
     * Start to monitor all the readers
     */
    public void startListening(){
        votes.clear();
        buffer.clear();
        time = new Timer();

        task = new TimerTask() {
            public void run() {
                recheckOnParticipants();

            }
        };


        time.schedule(task,timeout);
        this.sendNewVotes.set(false);
        this.ready.set(false);

    }


    /**
     * Shutdown all the readers
     */
    public void shutdownReaders(){
        abort.set(true);
        readers.forEach(PeerReadThread::shutdown);
    }

    /**
     * Shutdown the manager
     */
    public void abort(){
        abort.set(true);
    }


    /**
     * Get the new votes for this round
     * @return new votes
     */
    public Map<String,String> sendNewVotes(){


        Map<String,String> votesThisRound = new HashMap<>();
        buffer.values().forEach(vote -> {
            List<String> ports = vote.get_ports().stream()
                    .map( port -> Long.toString(port))
                    .collect(Collectors.toList());
            List<String> outcomes = vote.get_outcome();

            Map<String, String> map = IntStream.range(0, ports.size())
                    .boxed()
                    .collect(Collectors.toMap(ports::get, outcomes::get));

            votesThisRound.putAll(map);

        });


        sendNewVotes.set(false);
        buffer.clear();
        return votesThisRound;

    }

    /**
     * Check if a new round has started
     * @return
     */
    public boolean newVotesNotification(){

        return sendNewVotes.get();
    }


    /**
     * Get all the votes from all the rounds
     * @return all the votes ever received
     */
    public Map<Long,String> getVotes(){
        Map<Long,String> tmp = Map.copyOf(votes);
        System.out.println("All the votes have been collected");
        votes.clear();
        ready.set(false);
        return tmp;
    }


    /**
     * Number of established readers
     * @return number of readers
     */
    public int getConnectedClients(){
        return readers.size();
    }

    /**
     * Check if all the votes have been received
     * @return end of a run
     */
    public boolean isReady(){
        return ready.get();
    }

}
