import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class Coordinator {
    private int LISTENING_PORT;
    private int MAX_CONNECTIONS;
    private List<String> options = new ArrayList<>();
    private List<Integer> participants = new CopyOnWriteArrayList<>();
    private List<MessageToken.OutcomeToken> outcomes = new CopyOnWriteArrayList<MessageToken.OutcomeToken>();
    private AtomicBoolean resultReady = new AtomicBoolean(false);
    private Timer time = new Timer();

    public static void main(String[] args){

        if(args.length < 3){
            System.out.println("Not enough arguments...");
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
            System.out.println("Waiting for connections...");

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
                               if((line = in.readLine()) != null) {
                                   MessageToken.Token newToken = msg.getToken(line);
                                   if (newToken instanceof MessageToken.JoinToken){
                                       boolean hold = true;
                                       System.out.println("A Join Token was received by the server...");
                                       MessageToken.JoinToken msgJoin = (MessageToken.JoinToken) newToken;
                                       participants.add(msgJoin.get_port());
                                       port = msgJoin.get_port();

                                       // wait until all the participants have connected
                                       while (hold)
                                       {
                                           if(participants.size() == MAX_CONNECTIONS){
                                               out.write(sendParticipants(port));
                                               out.newLine();
                                               System.out.println(MessageFormat.format("A Details Token was sent by the server to the port {0} ...",port));
                                               out.write(sendVoteOptions(false));
                                               out.newLine();
                                               System.out.println(MessageFormat.format("A Vote Options Token was sent by the server to the port {0} ...",port));
                                               out.flush();
                                               hold=false;

                                           }
                                       }

                                   }

                                   else if (newToken instanceof MessageToken.OutcomeToken) {
                                       MessageToken.OutcomeToken msgOutcome = (MessageToken.OutcomeToken) newToken;
                                       outcomes.add(msgOutcome);

                                       String ports = msgOutcome.get_ports()
                                               .stream()
                                               .map(Object::toString)
                                               .collect(Collectors.joining(" "));

                                       String message = MessageFormat.format("Outcome result : {0} from {1}",msgOutcome.getOutcome(),ports);
                                       if(msgOutcome.getOutcome().equals("null")){
                                           message = MessageFormat.format("Outcome result : TIE from {0}",ports);
                                       }

                                       System.out.println(message);
                                       time.cancel();

                                       TimerTask task = new TimerTask() {
                                               public void run() {
                                                   checkOutcomes();
                                               }
                                           };
                                           time = new Timer();
                                           time.schedule(task,1000*participants.size());



                                       new Thread( () -> {

                                           while (true){
                                               if((outcomes.size() == participants.size()) || resultReady.get() ){
                                                   time.cancel();
                                                   List<MessageToken.OutcomeToken> outTmp = new ArrayList<>(outcomes);
                                                   boolean sameResult = outTmp.stream()
                                                           .map(MessageToken.OutcomeToken::getOutcome)
                                                           .distinct()
                                                           .count() <= 1;

                                                   if(sameResult){
                                                       String result = "";
                                                       if (outTmp.size() > 0) {
                                                           result= outTmp.get(0).getOutcome();
                                                       }


                                                       if(result.equals("null")){
                                                           outTmp.clear();
                                                           resultReady.set(false);

                                                           try {
                                                               out.write(sendVoteOptions(true));
                                                               out.newLine();
                                                               System.out.println("New Vote Options Token was sent by the server...");
                                                               out.flush();
                                                               outcomes = new CopyOnWriteArrayList<MessageToken.OutcomeToken>();
                                                               break;
                                                           } catch (IOException e) {
                                                               e.printStackTrace();
                                                           }



                                                       }
                                                       else {

                                                           String success = MessageFormat.format("Success: Outcome is {0}",result);
                                                           System.out.println(success);
                                                           String congrats = MessageFormat.format("Congrats to {0}",result);
                                                           System.out.println(congrats);
                                                           System.exit(0);
                                                       }


                                                   } else {
                                                       String error = "I did not get the same outcome from all participants. Error ";
                                                       System.out.println(error);
                                                       System.exit(0);
                                                   }

                                               }
                                           }

                                       }).start();

                                   }
                               }
                           } catch (IOException e) {
                           }

                       }
                   } catch (IOException e) {
                   }
               }).start();
           }




        } catch (IOException e) {
            String message = MessageFormat.format("Could start listening/accept connections on the port {0} ...",LISTENING_PORT);
            System.out.println(message);
        }


    }


    /**
     * The Details Options as a String
     * @param client the client to be excluded from the list of participants
     * @return the participants that are connected
     */
    private String sendParticipants(Integer client){
        String ports = participants
                .stream()
                .filter(port -> !port.equals(client))
                .map(Object::toString)
                .collect(Collectors.joining(" "));

        return MessageFormat.format("DETAILS {0}", ports);
    }

    /**
     * The Vote Options as a String
     * @param fewer check if restart
     * @return all vote options for this run
     */
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


    /**
     * Check if the Coordinator should wait for anymore new outcome messages
     */
    private synchronized void checkOutcomes(){
        if(outcomes.size() != participants.size()){
            resultReady.set(true);
        }
    }



}
