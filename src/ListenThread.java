import javax.sound.midi.SysexMessage;
import java.io.*;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ListenThread implements Runnable {
    private int timeout;
    private AtomicBoolean ready = new AtomicBoolean(false);
    private AtomicBoolean abort = new AtomicBoolean(false);
    private AtomicBoolean sendNewVotes = new AtomicBoolean(false);
    private int maxClients;
    private int failCond;
    private MessageToken.VoteToken vote = null;
    private Queue<PeerReadThread> readers = new ConcurrentLinkedQueue<>();

    Map<Long,String> votes = new ConcurrentHashMap<>();
    private Map<Long,String> lastState;
    private AtomicInteger maxClientsInitially = new AtomicInteger(0);
    private ExecutorService ex;
//    private AtomicBoolean roundsLeft = new AtomicBoolean(true);
    private Map<PeerReadThread,MessageToken.VoteToken> propagateNewVotes = new ConcurrentHashMap<>();
    private Timer time = new Timer();
    private TimerTask task;
    private AtomicBoolean noMoreReadings = new AtomicBoolean(false);


    public ListenThread(int timeout,int maxConnections,int fail) {
        this.timeout=timeout;
        maxClients=maxConnections;
        failCond=fail;

        task = new TimerTask() {
            public void run() {
                if(propagateNewVotes.size() > 0){
                    Map<Long,String> votesThisRound = new HashMap<>();

                    propagateNewVotes.values().forEach( vote -> {
                        List<Long> ports = vote.get_ports();
                        List<String> outcomes = vote.get_outcome();

                        Map<Long, String> map = IntStream.range(0, ports.size())
                                .boxed()
                                .collect(Collectors.toMap(ports::get, outcomes::get));

                        votesThisRound.putAll(map);

                    });

                    lastState = new ConcurrentHashMap<>(votes);
                    votes.putAll(votesThisRound);
                    sendNewVotes.set(true);

                    if(lastState.equals(votes)){
                        ready.set(true);
                    }


//                    Participant.logger.log(Level.INFO,"???End of a round");
                    Participant.logger.log(Level.INFO,"Size of votes: {0}",votes.size());
//                    Participant.logger.log(Level.INFO,"Max clients initially: {0}",maxClientsInitially);

                }
            }
        };


    }


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


                if(votes.keySet().size() >= maxClientsInitially.get()){
                    ready.set(true);
                }

                if(!sendNewVotes.get()){
//                    System.out.println("Size votes so far: " +propagateNewVotes.size());
                        Iterator<PeerReadThread> readerIterator = readers.iterator();

                        while (readerIterator.hasNext())
                        {
                            PeerReadThread nextReader = readerIterator.next();

                            if(nextReader.isReady()){


                                propagateNewVotes.put(nextReader,nextReader.getToken());
                                if(failCond == 1){
                                    sendNewVotes.set(true);
                                }
                                time.cancel();
                                time = new Timer();
                                TimerTask tmp = new TimerTask() {
                                    public void run() {
                                        if(propagateNewVotes.size() > 0){
                                            Map<Long,String> votesThisRound = new HashMap<>();

                                            propagateNewVotes.values().forEach( vote -> {
                                                List<Long> ports = vote.get_ports();
                                                List<String> outcomes = vote.get_outcome();

                                                Map<Long, String> map = IntStream.range(0, ports.size())
                                                        .boxed()
                                                        .collect(Collectors.toMap(ports::get, outcomes::get));

                                                votesThisRound.putAll(map);

                                            });
                                            lastState = new ConcurrentHashMap<>(votes);
                                            votes.putAll(votesThisRound);

                                            if(lastState.equals(votes)){
                                                ready.set(true);
                                            }
                                            sendNewVotes.set(true);


//                                            Participant.logger.log(Level.INFO,"???End of a round");
                                            Participant.logger.log(Level.INFO,"Size of votes: {0}",votes.size());
                                            Participant.logger.log(Level.INFO,"Max clients initially: {0}",maxClientsInitially);

                                        }
                                    }
                                };
                                time.schedule(tmp,timeout);
                                Participant.logger.log(Level.INFO,MessageFormat.format("Message received: {0} {1}",nextReader.getToken().get_outcome(),nextReader.getToken().get_ports()));
                                Participant.logger.log(Level.INFO,MessageFormat.format("Propagate size: {0}",propagateNewVotes.size()));

//                                Participant.logger.log(Level.INFO,MessageFormat.format("Current number of participants: {0}",maxClients));
                            }

//                            if(nextReader.isClosed()){
//                                maxClients--;
////                                roundsLeft.set(true);
//                                Participant.logger.log(Level.INFO,MessageFormat.format("Participant removed. There are: {0}",maxClients));
//                                readerIterator.remove();
//                            }

//                            if(nextReader.exhausedTime()){
//                                exhausedReaders.add(nextReader);
//                                readerIterator.remove();
//                            }

                        }


                    }

            }

        }

    }


    public void shutdownReaders(){
        abort.set(true);
        readers.forEach(PeerReadThread::shutdown);
//        ex.shutdown();
    }

    public void abort(){
        abort.set(true);
    }


    public Map<String,String> sendNewVotes(){


        Map<String,String> votesThisRound = new HashMap<>();

        propagateNewVotes.values().forEach( vote -> {
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

//        String message =  votesThisRound.entrySet().stream()
//                .map(e -> e.getKey() + " " + e.getValue())
//                .collect(Collectors.joining(" "));

        propagateNewVotes.clear();

        return votesThisRound;

//        return "VOTE " + message;
    }

    public boolean newVotesNotification(){

        return sendNewVotes.get();
    }


    public Map<Long,String> getVotes(){
//        MessageToken.VoteToken temp = vote;
        Map<Long,String> tmp = Map.copyOf(votes);
        Participant.logger.log(Level.INFO,"All the votes have been collected");
        votes.clear();
        ready.set(false);
        return tmp;
    }



    public int getConnectedClients(){
        return readers.size();
    }

    public boolean isReady(){
        return ready.get();
    }



}
