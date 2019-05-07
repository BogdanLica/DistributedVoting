import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Participant {
    private int LISTENING_PORT;
    private int COORDINATOR_PORT;
//    private int NO_PARTICIPANTS;
    private int TIMEOUT;
    private int failCond;
    private Queue<Integer> ports = new ConcurrentLinkedQueue<Integer>();
    private Map<String,BufferedWriter> _writeMap = Collections.synchronizedMap(new HashMap<>());
    private Map<String,BufferedReader> _readMap = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args){
        Participant me = new Participant(Integer.parseInt(args[0]),Integer.parseInt(args[1]),Integer.parseInt(args[2]),Integer.parseInt(args[3]));
        me.startListening();



    }

    public Participant(int coordinator,int myPort, int timeout, int fail){
        LISTENING_PORT = myPort;
        COORDINATOR_PORT = coordinator;
        TIMEOUT = timeout;
        failCond = fail;
    }

    private void startListening(){

        new Thread( () -> {
            try {
                ServerSocket listen = new ServerSocket(LISTENING_PORT) ;
                MessageToken msg = new MessageToken();

                Socket s = listen.accept();

                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(s.getOutputStream()));
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(s.getInputStream()));
                String line = null;
                while ((line = in.readLine()) != null) {
                    MessageToken.Token newToken = msg.getToken(line);
                    System.out.println(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }).start();
    }


    private void contactCoordinator(){
        new Thread(() -> {

            try {
                Socket coordinator = new Socket("localhost",COORDINATOR_PORT);

                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(coordinator.getOutputStream()));
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(coordinator.getInputStream()));


                String line = null;
                while ((line = in.readLine()) != null) {
                    System.out.println(line);
                }



            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void peerConnection(List<Integer> ports){
        List<Socket> connections = new ArrayList<>();
        ports.forEach(
                port -> {
                    new Thread( () -> {

                        try {
                            Socket peer = new Socket("localhost", port);

                            BufferedWriter out = new BufferedWriter(
                                    new OutputStreamWriter(peer.getOutputStream()));
                            BufferedReader in = new BufferedReader(
                                    new InputStreamReader(peer.getInputStream()));

                            connections.add(peer);
                            while (true) {
                                out.write("Hello World from port " + LISTENING_PORT);
                                out.newLine();
                                out.write("Currently there are " + connections.size() + " clients connected");
                                out.flush();

                                Thread.sleep(200);
                            }
                        } catch (UnknownHostException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                    }).start();
                }
        );
    }
}
