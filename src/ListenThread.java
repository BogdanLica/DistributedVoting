import javax.sound.midi.SysexMessage;
import java.io.*;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class ListenThread implements Runnable {
    private int timeout;
    private AtomicBoolean ready = new AtomicBoolean(false);
    private AtomicBoolean abort = new AtomicBoolean(false);
    private int maxClients;
    private MessageToken.VoteToken vote = null;
    private Queue<PeerReadThread> readers = new ConcurrentLinkedQueue<>();
    private Queue<PeerWriteThread> writers = new ConcurrentLinkedQueue<>();
//    Set<MessageToken.VoteToken> votes = new CopyOnWriteArraySet<>();
    Map<PeerReadThread,MessageToken.VoteToken> votes = new ConcurrentHashMap<>();
    private String writeBuffer;
    private ExecutorService ex;


    public ListenThread(int timeout,int maxConnections) {
        this.timeout=timeout;
        maxClients=maxConnections;
    }


    public void addClient(Socket client){
        PeerReadThread reader = new PeerReadThread(client);
        readers.offer(reader);

//        PeerWriteThread writer = new PeerWriteThread(client);
//        writers.offer(writer);

//        new Thread(reader).start();
//        new Thread(writer).start();
    }

    @Override
    public void run() {



        ex = Executors.newFixedThreadPool(readers.size());
        readers.forEach( reader -> ex.submit(reader));



        while (!abort.get()){
            if(!ready.get()){

                String message;
                if(readers.size() == maxClients && votes.size() == maxClients){
                    ready.set(true);
//                message ="ListenThread ready";
//                Participant.logger.log(Level.INFO,message);
                }
                else {
                    readers.forEach(reader -> {
                        if(reader.isReady()){
                            if(!votes.containsKey(reader)){
                                String msg = MessageFormat.format("Number of votes received: {0} ...",votes.size());
                                Participant.logger.log(Level.INFO,msg);
                            }
                            //Participant.logger.log(Level.INFO,"Saving vote sent...");
                            votes.putIfAbsent(reader,reader.getToken());
                        }
                    });
                }

            }
//            String message = MessageFormat.format("Number of votes received: {0} ...",votes.size());
//            Participant.logger.log(Level.INFO,message);
////
//            message = MessageFormat.format("Number of readers: {0} ...",readers.size());
//            Participant.logger.log(Level.INFO,message);


//            if(writeBuffer != null){
//                writers.forEach(writer -> {
//                    writer.write(writeBuffer);
//                });
//                writeBuffer=null;
//            }
        }

    }

//    public void sendVote(String vote){
//        writeBuffer = vote;
//    }
//    private void saveToken(List<MessageToken.VoteToken> tokens){
//
//        tokens.forEach(token -> {
//            if(token != null)
//            {
//                vote.add(token);
//            }
//        });
//
//    }


    public void shutdownReaders(){
        abort.set(true);
        ex.shutdown();
//        readers.forEach(PeerReadThread::shutdown);
    }


    public Collection<MessageToken.VoteToken> getVotes(){
//        MessageToken.VoteToken temp = vote;
        ready.set(false);
        Collection<MessageToken.VoteToken> tmp = votes.values();
//        return temp;
        votes.clear();
        return tmp;
    }

    public int getConnectedClients(){
        return readers.size();
    }

    public boolean isReady(){
        return ready.get();
    }



}
