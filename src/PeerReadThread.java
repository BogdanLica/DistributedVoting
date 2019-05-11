import java.io.*;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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


    public PeerReadThread(Socket client){
        this._client=client;



        try {
//            writer = new BufferedWriter(new OutputStreamWriter(_client.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(_client.getInputStream()));

        } catch (IOException e) {
//            e.printStackTrace();
            String message = MessageFormat.format("Reader could not be created for port {0} ...",_client.getPort());
            Participant.logger.log(Level.WARNING,message);

        }
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

            } catch (IOException e)
            {
                String message = MessageFormat.format("Could not read from port {0} ...",_client.getPort());
                Participant.logger.log(Level.WARNING,message);
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
    }

    public boolean isReady(){
        return ready.get();
    }


}
