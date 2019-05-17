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
    private AtomicReference<String> buffer = new AtomicReference<>("");
    private AtomicBoolean abortWrite = new AtomicBoolean(false);

    /**
     * A thread that given a socket, it will send messages to the socket and
     * @param client the socket of the client connected to
     */
    public PeerWriteThread(Socket client){
        this._client=client;
    }

    /**
     * If the Participant class specifies that wants to send a message by putting it in this buffer
     * @param tmp the string to be sent in the future to the socket of the client
     */
    public void write(String tmp){
        buffer.set(tmp);
    }


    /**
     * Write the given string to the socket
     * @param text the string to be sent across
     */
    private void writeToSocket(String text){
        try {
            String message = MessageFormat.format("Sending message {0} to port {1}",text,_client.getPort());
            System.out.println(message);
            writer.write(text);
            writer.newLine();
            writer.flush();

        } catch (IOException e) {
            abortWrite.set(true);
        }
    }


    /**
     * Shutdown the writer
     */
    public void shutdown(){
        try {
            writer.close();
            _client.close();
        } catch (IOException e) {
            System.out.println("Closed connection to a peer");
        }

    }

    @Override
    public void run() {

        try {
            writer = new BufferedWriter(new OutputStreamWriter(_client.getOutputStream()));

            String message = MessageFormat.format("New connection accepted from port {0}",_client.getPort());
            System.out.println(message);

            while (true){

                if(!buffer.get().equals("")){
                    this.writeToSocket(buffer.get());
                    buffer.set("");
                }


            }

        } catch (IOException e) {

        }

    }
}
