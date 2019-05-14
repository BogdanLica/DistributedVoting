import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.sql.Time;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public class PeerReadThread implements Runnable {
    private Socket _client;
    private BufferedReader reader;
    private AtomicBoolean ready = new AtomicBoolean(false);
//    private  vote = null;
//    private List<String> buffer = new CopyOnWriteArrayList<>();
    private AtomicReference<MessageToken.VoteToken> buffer = new AtomicReference<>();
    private AtomicBoolean running = new AtomicBoolean(true);
    private AtomicBoolean closed = new AtomicBoolean(false);
    private int timeout;


    public PeerReadThread(Socket client,int timeout){
        this._client=client;




        try {
//            writer = new BufferedWriter(new OutputStreamWriter(_client.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(_client.getInputStream()));

        } catch (IOException e) {
//            e.printStackTrace();
            String message = MessageFormat.format("Reader could not be created for port {0} ...",_client.getPort());
            Participant.logger.log(Level.WARNING,message);

        }

//        try {
//            _client.setSoTimeout(timeout);
//        } catch (SocketException e) {
//            closed.set(true);
//        }


    }



    public MessageToken.VoteToken getToken(){
        ready.set(false);
        return buffer.get();
    }



    @Override
    public void run() {

        MessageToken msg = new MessageToken();


        List<MessageToken.VoteToken> newTokens = new ArrayList<>();


        while (running.get()){
            try {
                String line = null;

                if(!ready.get()){
                    if ((line = reader.readLine()) != null) {

                        MessageToken.Token newToken = msg.getToken(line);
                        if(newToken instanceof MessageToken.VoteToken)
                        {

//                            String message = MessageFormat.format("Received message {0}",((MessageToken.VoteToken)newToken).get_outcome());
//                            Participant.logger.log(Level.INFO,message);
                            buffer.set((MessageToken.VoteToken)newToken);
//                            newTokens.add(vote);
//                            saveToken(newTokens);
                            ready.set(true);
                        }

//                    System.out.println(line);
                    }
                }


//                Thread.sleep(1000);

            }
//            catch (SocketException e) {
//                closed.set(true);
//            }
            catch (IOException e)
            {
//                String message = MessageFormat.format("Could not read from port {0} ...",_client.getPort());
//                Participant.logger.log(Level.WARNING,message);
//                closed.set(true);
//                running.set(false);
            }
//            catch (InterruptedException e){
//                Thread.currentThread().interrupt();
//            }
//            catch (InterruptedException e){
//                String message = MessageFormat.format("Could not sleep thread {0} ...",Thread.currentThread().getName());
//                Participant.logger.log(Level.WARNING,message);
//            }


        }

    }


    public void shutdown(){
        running.set(false);
        try {
            reader.close();
            _client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isReady(){
        return ready.get();
    }


    public boolean isClosed() {
        return closed.get();
    }
}
