import java.io.*;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class ListenThread implements Runnable {
    private Socket _client;
    // private BufferedWriter writer;
    private BufferedReader reader;
    private int timeout;
    private boolean ready;
    private List<MessageToken.VoteToken> messages = new ArrayList<>();


    public ListenThread(Socket socket,int timeout) {
        _client = socket;
        this.timeout=timeout;
    }

    @Override
    public void run() {
        MessageToken msg = new MessageToken();


        try {
//            writer = new BufferedWriter(new OutputStreamWriter(_client.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(_client.getInputStream()));

        } catch (IOException e) {
//            e.printStackTrace();
            String message = MessageFormat.format("Reader could not be created for port {0} ...",_client.getPort());
            Participant.logger.log(Level.WARNING,message);

        }

        while (true){

            try {
                String line = null;
                List<MessageToken.Token> newTokens = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    MessageToken.Token newToken = msg.getToken(line);
                    newTokens.add(newToken);
//                    System.out.println(line);
                }

                saveToken(newTokens);
                ready=true;
                Thread.sleep(timeout);

            } catch (IOException e)
            {
                String message = MessageFormat.format("Could not read from port {0} ...",_client.getPort());
                Participant.logger.log(Level.WARNING,message);
            }
            catch (InterruptedException e){
                String message = MessageFormat.format("Could not sleep thread {0} ...",Thread.currentThread().getName());
                Participant.logger.log(Level.WARNING,message);
            }


        }

    }

    private void saveToken(List<MessageToken.Token> tokens){

        tokens.forEach(token -> {
            if(token instanceof MessageToken.VoteToken)
            {
                messages.add((MessageToken.VoteToken) token);
            }
        });

    }



    public List<MessageToken.VoteToken> getTokens(){
        List<MessageToken.VoteToken> temp = new ArrayList<>(messages);
        messages.clear();
        ready = false;
        return temp;
    }


    public boolean anyTokens(){
        return messages.size() > 0 && ready;
    }



}
