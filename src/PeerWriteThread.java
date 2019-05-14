import java.io.*;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public class PeerWriteThread implements Runnable {
    private Socket _client;
    private BufferedWriter writer;
//    private List<String> buffer = new CopyOnWriteArrayList<>();
    private AtomicReference<String> buffer = new AtomicReference<>("");
    private AtomicBoolean abortWrite = new AtomicBoolean(false);

    public PeerWriteThread(Socket client){
        this._client=client;
    }

    public void write(String tmp){
        buffer.set(tmp);
    }


    private void writeToSocket(String text){
        try {
            String message = MessageFormat.format("Sending message {0} to port {1}",text,_client.getPort());
            Participant.logger.log(Level.INFO,message);
            writer.write(text);
            writer.newLine();
            writer.flush();

        } catch (IOException e) {
            abortWrite.set(true);
//            String message = MessageFormat.format("Could not write to port {0}",_client.getPort());
//            Participant.logger.log(Level.WARNING,message);
        }
    }



    public void shutdown(){
        try {
            writer.close();
//            _client.shutdownOutput();
            _client.close();
        } catch (IOException e) {
//            e.printStackTrace();
            Participant.logger.log(Level.INFO,"Closed connection to a peer");
        }

    }


    public boolean getStatusUP(){
        return abortWrite.get();
    }


    public Integer getPort(){
        return _client.getPort();
    }



//    public Integer getPortNumber(){
//        return _client.getPort();
//    }



    @Override
    public void run() {

        try {
            writer = new BufferedWriter(new OutputStreamWriter(_client.getOutputStream()));
//            reader = new BufferedReader(new InputStreamReader(_client.getInputStream()));

            String message = MessageFormat.format("New connection accepted from port {0}",_client.getPort());
            Participant.logger.log(Level.INFO,message);

            while (true){

                if(!buffer.get().equals("")){
                    this.writeToSocket(buffer.get());
                    buffer.set("");
                }

//                Thread.sleep(1000);

            }

        } catch (IOException e) {
//            e.printStackTrace();
            String message = MessageFormat.format("Writer or Reader could not be created for port {0}",_client.getPort());
            Participant.logger.log(Level.WARNING,message);

        }
//        catch (InterruptedException e){
//            Thread.currentThread().interrupt();
//        }

    }
}
