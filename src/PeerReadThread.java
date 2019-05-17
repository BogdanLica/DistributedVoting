import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class PeerReadThread implements Runnable {
    private Socket _client;
    private BufferedReader reader;
    private AtomicBoolean ready = new AtomicBoolean(false);
    private AtomicReference<MessageToken.VoteToken> buffer = new AtomicReference<>();
    private AtomicBoolean running = new AtomicBoolean(true);
    private AtomicBoolean closed = new AtomicBoolean(false);
    private int timeout;

    /**
     * A thread that given a socket, it will listen for messages and
     * when there is a message, it will put it in a buffer
     * @param client the socket of the client connected to
     * @param timeout after the time is exceeded without any new read, the client is considered to be disconnected
     */
    public PeerReadThread(Socket client,int timeout){
        this._client=client;




        try {
            reader = new BufferedReader(new InputStreamReader(_client.getInputStream()));

        } catch (IOException e) {

        }

    }


    /**
     * Return the next message read from the socket into the buffer
     * @return the message received from the socket
     */
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

                            buffer.set((MessageToken.VoteToken)newToken);
                            ready.set(true);
                        }

                    }
                }



            }
            catch (IOException e)
            {
            }

        }

    }

    /**
     * Shutdown the reader
     */
    public void shutdown(){
        running.set(false);
        try {
            reader.close();
            _client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Check if the buffer has been filled
     * @return status of buffer
     */
    public boolean isReady(){
        return ready.get();
    }
}
