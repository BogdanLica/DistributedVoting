import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Coordinator {
    private int LISTENING_PORT;
    private int MAX_CONNECTIONS;
    private List<String> options = new ArrayList<>();
//    private List<Socket> participants = new ArrayList<>(MAX_CONNECTIONS);
//    private Map<Socket, BufferedReader> readers = Collections.synchronizedMap(new HashMap<>(MAX_CONNECTIONS));
//    private Map<Socket, BufferedWriter> writers = Collections.synchronizedMap(new HashMap<>(MAX_CONNECTIONS));
    private List<Integer> participants = new CopyOnWriteArrayList<>();
    private Map<MessageToken.Token,Integer> tokenToPort = Collections.synchronizedMap(new HashMap<>(MAX_CONNECTIONS));
    private List<MessageToken.OutcomeToken> outcomes = new CopyOnWriteArrayList<MessageToken.OutcomeToken>();
    public final static Logger logger = Logger.getLogger(Coordinator.class.getName());
    private AtomicBoolean resultReady = new AtomicBoolean(false);
    private Timer time = new Timer();

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
                                               logger.log(Level.INFO,"A Details Token was sent by the server...");
                                               out.write(sendVoteOptions(false));
                                               out.newLine();
                                               logger.log(Level.INFO,"A Vote Options Token was sent by the server...");
                                               out.flush();
                                               hold=false;

                                           }
                                       }

                                   }

                                   else if (newToken instanceof MessageToken.OutcomeToken) {
                                       MessageToken.OutcomeToken msgOutcome = (MessageToken.OutcomeToken) newToken;
//                                       time.cancel();
                                       outcomes.add(msgOutcome);

                                       String ports = msgOutcome.get_ports()
                                               .stream()
                                               .map(Object::toString)
                                               .collect(Collectors.joining(" "));

                                       String message = MessageFormat.format("Outcome result : {0} from {1}",msgOutcome.getOutcome(),ports);
                                       logger.log(Level.INFO,message);
                                       time.cancel();

                                       TimerTask task = new TimerTask() {
                                               public void run() {
                                                   checkOutcomes();
                                               }
                                           };
                                           time = new Timer();
                                           time.schedule(task,1000*participants.size());

//                                       if(outcomes.size() == participants.size()){
//                                           resultReady.set(true);
//                                       }
//                                       else{
//                                           TimerTask task = new TimerTask() {
//                                               public void run() {
//                                                   if(outcomes.size() != participants.size()){
//                                                       resultReady.set(true);
//                                                   }
//                                               }
//                                           };
//                                           time = new Timer();
//                                           time.schedule(task,1000*participants.size());
//                                       }


                                       new Thread( () -> {

                                           while (true){
                                               if((outcomes.size() == participants.size()) || resultReady.get() ){
                                                   time.cancel();
                                                   List<MessageToken.OutcomeToken> outTmp = new ArrayList<>(outcomes);
//                                                   time.cancel();
//                                                   logger.log(Level.INFO,"I'm inside...");
                                                   boolean sameResult = outTmp.stream()
                                                           .map(MessageToken.OutcomeToken::getOutcome)
                                                           .distinct()
                                                           .count() <= 1;

                                                   if(sameResult){
                                                       String result = outTmp.get(0).getOutcome();

                                                       if(result.equals("null")){
                                                           outTmp.clear();
                                                           resultReady.set(false);

                                                           try {
                                                               out.write(sendVoteOptions(true));
                                                               out.newLine();
                                                               logger.log(Level.INFO,"New Vote Options Token was sent by the server...");
                                                               out.flush();
                                                               outcomes = new CopyOnWriteArrayList<MessageToken.OutcomeToken>();
                                                               break;
                                                           } catch (IOException e) {
                                                               e.printStackTrace();
                                                           }



                                                       }
                                                       else {

                                                           String success = MessageFormat.format("Outcome: {0}",outTmp.get(0).getOutcome());
                                                           logger.log(Level.INFO,success);
                                                           String congrats = MessageFormat.format("Congrats to {0}",outTmp.get(0).get_ports());
                                                           logger.log(Level.INFO,congrats);
                                                           System.exit(0);
                                                       }


                                                   } else {
                                                       String error = "I did not get the same outcome from all participants. Error ";
                                                       logger.log(Level.WARNING,error);
                                                       System.exit(0);
                                                   }

                                               }
                                           }

                                       }).start();

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

    private synchronized String sendVoteOptions(boolean fewer){
        String options = "";
        if (fewer){
            List<String> tmp = new ArrayList<>(this.options);
            tmp.remove(tmp.size()-1);
            options = String.join(" ", tmp);

        }
        else {
            options = String.join(" ", this.options);
        }


        return MessageFormat.format("VOTE_OPTIONS {0}", options);
    }



    private synchronized void checkOutcomes(){
        if(outcomes.size() != participants.size()){
            resultReady.set(true);
        }
    }



}
