import java.io.*;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.logging.Level;

public class PeerThread implements Runnable {
    private Socket _client;
    private BufferedWriter writer;
    private BufferedReader reader;

    public PeerThread(Socket client){
        this._client=client;
    }


    public void writeToSocket(String text){
        try {
            writer.write(text);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            String message = MessageFormat.format("Could not write to port {0}",_client.getPort());
            Participant.logger.log(Level.WARNING,message);
        }
    }

    public String readFromSocket(){
        try {
            return reader.readLine();

        } catch (IOException e) {
            String message = MessageFormat.format("Could not read from port {0}",_client.getPort());
            Participant.logger.log(Level.WARNING,message);
        }
        return null;
    }



    @Override
    public void run() {

        try {
            writer = new BufferedWriter(new OutputStreamWriter(_client.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(_client.getInputStream()));

        } catch (IOException e) {
//            e.printStackTrace();
            String message = MessageFormat.format("Writer or Reader could not be created for port {0}",_client.getPort());
            Participant.logger.log(Level.WARNING,message);

        }

    }
}
