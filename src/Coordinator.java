import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Coordinator {
    private int LISTENING_PORT;
    private int MAX_CONNECTIONS;
//    private List<Socket> participants = new ArrayList<>(MAX_CONNECTIONS);
//    private Map<Socket, BufferedReader> readers = Collections.synchronizedMap(new HashMap<>(MAX_CONNECTIONS));
//    private Map<Socket, BufferedWriter> writers = Collections.synchronizedMap(new HashMap<>(MAX_CONNECTIONS));
    private List<PeerThread> participants = new ArrayList<>(MAX_CONNECTIONS);

    public static void main(String[] args){

    }

    public Coordinator(int myPort,int noParticipants){
        LISTENING_PORT = myPort;
        MAX_CONNECTIONS = noParticipants;

    }

    public void start(){

        try {
            ServerSocket listen = new ServerSocket(LISTENING_PORT) ;
            MessageToken msg = new MessageToken();

            Socket s = null;
            s = listen.accept();
            PeerThread client = new PeerThread(s);
            new Thread(client).start();
//            BufferedWriter out = new BufferedWriter(
//                    new OutputStreamWriter(s.getOutputStream()));
//            BufferedReader in = new BufferedReader(
//                    new InputStreamReader(s.getInputStream()));
//            String line = null;
//            while ((line = in.readLine()) != null) {
//                MessageToken.Token newToken = msg.getToken(line);
//                System.out.println(line);
//            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private void sendParticipants(){

    }

    private void sendVoteOptions(){}




}
