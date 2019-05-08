import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Coordinator {
    private int LISTENING_PORT;
    private int MAX_CONNECTIONS;
    private List<String> options = new ArrayList<>();
//    private List<Socket> participants = new ArrayList<>(MAX_CONNECTIONS);
//    private Map<Socket, BufferedReader> readers = Collections.synchronizedMap(new HashMap<>(MAX_CONNECTIONS));
//    private Map<Socket, BufferedWriter> writers = Collections.synchronizedMap(new HashMap<>(MAX_CONNECTIONS));
    private List<Integer> participants = Collections.synchronizedList(new ArrayList<>(MAX_CONNECTIONS));
    private Map<MessageToken.Token,Integer> tokenToPort = Collections.synchronizedMap(new HashMap<>(MAX_CONNECTIONS));
    public final static Logger logger = Logger.getLogger(Coordinator.class.getName());

    public static void main(String[] args){

        if(args.length < 3){
            logger.log(Level.SEVERE,"Not enough arguments...");
        }
        else {
            List<String> options = Arrays.stream(args)
                    .skip(2)
                    .collect(Collectors.toList());
            Coordinator server = new Coordinator(Integer.parseInt(args[0]),Integer.parseInt(args[1]),options);
            server.start();
        }

    }

    public Coordinator(int myPort,int noParticipants, List<String> newOptions){
        LISTENING_PORT = myPort;
        MAX_CONNECTIONS = noParticipants;
        options.addAll(newOptions);

    }

    public void start(){

//        try {
//            ServerSocket listen = new ServerSocket(LISTENING_PORT) ;
//            MessageToken msg = new MessageToken();
//
//            Socket s = null;
//            s = listen.accept();
//            PeerThread client = new PeerThread(s);
//            new Thread(client).start();
////            BufferedWriter out = new BufferedWriter(
////                    new OutputStreamWriter(s.getOutputStream()));
////            BufferedReader in = new BufferedReader(
////                    new InputStreamReader(s.getInputStream()));
////            String line = null;
////            while ((line = in.readLine()) != null) {
////                MessageToken.Token newToken = msg.getToken(line);
////                System.out.println(line);
////            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        try {
            ServerSocket listen = new ServerSocket(LISTENING_PORT);
            logger.log(Level.INFO,"Waiting for connections...");

           Socket client;

           while (participants.size() < MAX_CONNECTIONS){

               Socket finalClient = listen.accept();
               new Thread( () -> {
                   MessageToken msg = new MessageToken();
                   try {
                       BufferedWriter out = new BufferedWriter(new OutputStreamWriter(finalClient.getOutputStream()));
                       BufferedReader in = new BufferedReader(new InputStreamReader(finalClient.getInputStream()));
                       String line;
                       Integer port;
                       while (true) {
                           try {
                               //if ((line = in.readLine()) == null) Thread.sleep(1000);
                               if((line = in.readLine()) != null) {
                                   MessageToken.Token newToken = msg.getToken(line);
                                   if (newToken instanceof MessageToken.JoinToken){
                                       boolean hold = true;
                                       logger.log(Level.INFO,"A Join Token was received by the server...");
                                       MessageToken.JoinToken msgJoin = (MessageToken.JoinToken) newToken;
                                       participants.add(msgJoin.get_port());
                                       port = msgJoin.get_port();

                                       while (hold)
                                       {
//                                       tokenToPort.put(msgJoin,msgJoin.get_port());
                                           if(participants.size() == MAX_CONNECTIONS){
                                               out.write(sendParticipants(port));
                                               out.newLine();
                                               out.flush();
                                               hold=false;
                                               logger.log(Level.INFO,"A Details Token was sent by the server...");
                                           }
                                       }

                                   }
                               }
                           } catch (IOException e) {
                               //e.printStackTrace();
                               String message = MessageFormat.format("Could not read the next line on socket {0} ...",finalClient.getPort());
                               logger.log(Level.WARNING,message);
                           }
//                           catch (InterruptedException e) {
//                               //e.printStackTrace();
//                               String message = MessageFormat.format("Thread could not be suspended for client socket {0} ...",finalClient.getPort());
//                               logger.log(Level.WARNING,message);
//                           }

                       }
                   } catch (IOException e) {
//                    e.printStackTrace();
                       String message = MessageFormat.format("Writer/Reader could not be created for port {0} ...",finalClient.getPort());
                       logger.log(Level.WARNING,message);
                   }
               }).start();
           }




        } catch (IOException e) {
//            e.printStackTrace();

            String message = MessageFormat.format("Could start listening/accept connections on the port {0} ...",LISTENING_PORT);
            logger.log(Level.WARNING,message);
        }


    }



    private String sendParticipants(Integer client){
        String ports = participants
                .stream()
                .filter(port -> !port.equals(client))
                .map(Object::toString)
                .collect(Collectors.joining(" "));

        return MessageFormat.format("DETAILS {0}", ports);
    }

//    private String sendVoteOptions(){}




}
