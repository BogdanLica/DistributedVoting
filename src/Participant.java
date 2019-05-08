import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Participant {
    private int LISTENING_PORT;
    private int COORDINATOR_PORT;
//    private int NO_PARTICIPANTS;
    private int TIMEOUT;
    private int failCond;
    private AtomicBoolean sendOutcome = new AtomicBoolean(false);
    private Queue<Long> ports = new ConcurrentLinkedQueue<>();
//    private Map<String,BufferedWriter> _writeMap = Collections.synchronizedMap(new HashMap<>());
//    private Map<String,BufferedReader> _readMap = Collections.synchronizedMap(new HashMap<>());
    private Queue<PeerThread> peers = new LinkedList<>();
    ListenThread listenConnections;
//    PeerThread server;

    public final static Logger logger = Logger.getLogger(Participant.class.getName());

    public static void main(String[] args){
        Participant me = new Participant(Integer.parseInt(args[0]),Integer.parseInt(args[1]),Integer.parseInt(args[2]),Integer.parseInt(args[3]));
        me.contactCoordinator();
        me.startListening();



    }

    public Participant(int coordinator,int myPort, int timeout, int fail){
        LISTENING_PORT = myPort;
        COORDINATOR_PORT = coordinator;
        TIMEOUT = timeout;
        failCond = fail;
    }

    private void startListening(){
        ServerSocket listen = null;
        try {
            listen = new ServerSocket(LISTENING_PORT);
        } catch (IOException e) {
            //e.printStackTrace();
            String message = MessageFormat.format("Could start listening on the port {0}",Long.toString(LISTENING_PORT));
            logger.log(Level.WARNING,message);
        }

            try {
                Socket socket = listen.accept();
                listenConnections = new ListenThread(socket,TIMEOUT);
                new Thread(listenConnections).start();
            } catch (IOException e) {
                logger.log(Level.WARNING,"Could not accept a new client");
                //e.printStackTrace();
            }


//        new Thread( () -> {
//            try {
//                ServerSocket listen = new ServerSocket(LISTENING_PORT) ;
//                MessageToken msg = new MessageToken();
//
//                Socket s = listen.accept();
//
//                BufferedWriter out = new BufferedWriter(
//                        new OutputStreamWriter(s.getOutputStream()));
//                BufferedReader in = new BufferedReader(
//                        new InputStreamReader(s.getInputStream()));
//                String line = null;
//                while ((line = in.readLine()) != null) {
//                    MessageToken.Token newToken = msg.getToken(line);
//                    System.out.println(line);
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//        }).start();

    }


    private void sendOutcome(){

        if(listenConnections.anyTokens()){
            MessageToken.OutcomeToken result = decideOutcome(listenConnections.getTokens());
            sendOutcome.set(true);
            // send result to server
        }
    }


    private MessageToken.OutcomeToken decideOutcome(List<MessageToken.VoteToken> votes){

        return null;
    }



    private void contactCoordinator(){
        new Thread(() -> {

            try {
                Socket coordinator = new Socket("localhost",COORDINATOR_PORT);
                MessageToken msg = new MessageToken();

                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(coordinator.getOutputStream()));
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(coordinator.getInputStream()));

                logger.log(Level.INFO,"Connected to the server...");

                String line = null;

                /**
                 * TODO: send a join token
                 */

                out.write(MessageFormat.format("JOIN {0}",Long.toString(this.LISTENING_PORT)));
                out.newLine();
                out.flush();
                logger.log(Level.INFO,"A Join Token was just sent to the server...");

                while (true)
                {
                    while ((line = in.readLine()) != null) {
                        MessageToken.Token newToken = msg.getToken(line);

                        if(newToken instanceof MessageToken.DetailsToken) {
                            MessageToken.DetailsToken details = (MessageToken.DetailsToken) newToken;
                            ports.addAll(details.get_ports());
                            peerConnectionStart();
                            logger.log(Level.INFO,"Reading a Details Token...");
                        }

                        else if(newToken instanceof MessageToken.VoteOptionsToken) {
                            logger.log(Level.INFO,"Reading a Vote Option Token...");
                        }

                        /**
                         * TODO: Implement restart message
                         */
//                    else if(newToken instanceof )


                    }

                    if(sendOutcome.get())
                    {
                        out.write("MY outcome");
                        out.newLine();
                        out.flush();
                        sendOutcome.set(false);
                        logger.log(Level.INFO,"Sending the Outcome to the server...");
                        Thread.sleep(TIMEOUT);
                        // send outcome to coordinator
                        // sleep for timeout (time until the next round)
                    }

                }




            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void peerConnectionStart(){
        ports.forEach(port -> {
                Socket peer = null;
                try {
                    peer = new Socket("localhost", port.intValue());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                PeerThread client = new PeerThread(peer);
                peers.offer(client);

                new Thread(client).start();

                String message = MessageFormat.format("New connection established with port {0}",port);
                Participant.logger.log(Level.INFO,message);


            }
        );
//                    new Thread( () -> {
//
//                        try {
//
//
//                            BufferedWriter out = new BufferedWriter(
//                                    new OutputStreamWriter(peer.getOutputStream()));
//                            BufferedReader in = new BufferedReader(
//                                    new InputStreamReader(peer.getInputStream()));
//
//                            connections.add(peer);
//                            while (true) {
//                                out.write("Hello World from port " + LISTENING_PORT);
//                                out.newLine();
//                                out.write("Currently there are " + connections.size() + " clients connected");
//                                out.flush();
//
//                                Thread.sleep(200);
//                            }
//                        } catch (UnknownHostException e) {
//                            e.printStackTrace();
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//
//                    }).start();

    }
}
