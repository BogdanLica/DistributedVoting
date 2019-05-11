import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Participant {
    private Long LISTENING_PORT;
    private int COORDINATOR_PORT;
    private int TIMEOUT;
    private int failCond;
    private AtomicBoolean sendOutcomeReady = new AtomicBoolean(false);
    private Queue<Long> ports = new ConcurrentLinkedQueue<>();
    private Queue<PeerWriteThread> peers = new ConcurrentLinkedQueue<>();
    private List<String> options = new ArrayList<>();
    private volatile String outcome;
    private volatile String myVote;
    private ListenThread listenConnection;

    public final static Logger logger = Logger.getLogger(Participant.class.getName());

    public static void main(String[] args){
        Participant me = new Participant(Integer.parseInt(args[0]),Long.parseLong(args[1]),Integer.parseInt(args[2]),Integer.parseInt(args[3]));
        me.contactCoordinator();


    }

    public Participant(int coordinator,Long myPort, int timeout, int fail){
        LISTENING_PORT = myPort;
        COORDINATOR_PORT = coordinator;
        TIMEOUT = timeout;
        failCond = fail;
    }

    private void startListening(){
        ServerSocket listen = null;
        try {
            listen = new ServerSocket(LISTENING_PORT.intValue());
            listenConnection =  new ListenThread(TIMEOUT,ports.size());

            ServerSocket finalListen = listen;
            new Thread( () -> {
                while (listenConnection.getConnectedClients()<ports.size()){
                    try {

                        Socket socket = Objects.requireNonNull(finalListen).accept();
                        listenConnection.addClient(socket);
                    } catch (IOException e) {
                        logger.log(Level.WARNING,"Could not accept a new client");
                        //e.printStackTrace();
                    }

                }
                new Thread(listenConnection).start();
                new Thread(this::setOutcome).start();
            }).start();



        } catch (IOException e) {
            //e.printStackTrace();
            String message = MessageFormat.format("Could not start listening on the port {0}",Long.toString(LISTENING_PORT));
            logger.log(Level.WARNING,message);
        }


    }


    private void setOutcome(){
        while (true){
            if (!sendOutcomeReady.get()){
                if (listenConnection.isReady()){
                    String message = "Computing decision...";
                    Participant.logger.log(Level.INFO,message);


                    Map<String,Integer> result = new HashMap<>();
                    result.put(myVote,1);


                    listenConnection
                            .getVotes()
                            .forEach(voteToken -> {
                                if(result.containsKey(voteToken.get_outcome())){
                                    int tmp = result.get(voteToken.get_outcome());
                                    tmp++;
                                    result.put(voteToken.get_outcome(),tmp);
                                }
                                else {
                                    result.put(voteToken.get_outcome(),1);
                                }
                            });

//                    message = MessageFormat.format("Decision made with {0} votes...",result.values().stream().mapToInt(Integer::intValue).sum());
//                    Participant.logger.log(Level.INFO,message);


                    /**
                     * Check if different result are achieved over the course of multiple rounds
                     */
                    String allOptions = result.keySet()
                            .stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(" "));

                    String allValues = result.values()
                            .stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(" "));

                    message = MessageFormat.format("Options: {0}",allOptions);
                    Participant.logger.log(Level.INFO,message);
                    message = MessageFormat.format("Values: {0}",allValues);
                    Participant.logger.log(Level.INFO,message);



                    outcome = decideOutcome(result);
                    sendOutcomeReady.set(true);
                }
            }
        }

    }

    private String ownDecision() {
        int index = ThreadLocalRandom.current().nextInt(options.size());

        return options.get(index);
    }



    //TODO: take more than max
    private String decideOutcome(Map<String,Integer> result){
        int max = result.values().stream().max(Comparator.naturalOrder()).get();
        List<String> keysWithMaxValues = result.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return (keysWithMaxValues.size() > 1) ? "null" : keysWithMaxValues.get(0);

//        if(keysWithMaxValues.size() > 1) return "null"
//
//        return Collections.max(result.entrySet(), Comparator.comparingInt(Map.Entry::getValue)).getKey();
    }



    private void contactCoordinator() {

        try {
            Socket coordinator = new Socket("localhost", COORDINATOR_PORT);
            MessageToken msg = new MessageToken();

            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(coordinator.getOutputStream()));
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(coordinator.getInputStream()));

            //logger.log(Level.INFO,"Connected to the server...");

            String line = null;

            out.write(MessageFormat.format("JOIN {0}", Long.toString(this.LISTENING_PORT)));
            out.newLine();
            out.flush();
            logger.log(Level.INFO, "A Join Token was just sent to the server...");
            AtomicBoolean restartVote = new AtomicBoolean(false);

            new Thread( () -> {
                while (!restartVote.get()){
                    if(sendOutcomeReady.get()){
                        try {
                            out.write(sendOutcome());
                            out.newLine();
                            out.flush();

                            logger.log(Level.INFO, "Sending the Outcome to the server...");
                            myVote = ownDecision();
                            Thread.sleep(TIMEOUT*2);
                            peers.forEach(peer -> {
                                peer.write(MessageFormat.format("VOTE {0} {1}", Long.toString(LISTENING_PORT), myVote));
                            });
                            sendOutcomeReady.set(false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                }
            }).start();



            while (true) {
                if ((line = in.readLine()) != null) {
                    MessageToken.Token newToken = msg.getToken(line);

                    if (newToken instanceof MessageToken.DetailsToken) {
                        MessageToken.DetailsToken details = (MessageToken.DetailsToken) newToken;
                        ports.addAll(details.get_ports());
                        logger.log(Level.INFO, "Reading a Details Token...");


                    } else if (newToken instanceof MessageToken.VoteOptionsToken) {
                        MessageToken.VoteOptionsToken vote = (MessageToken.VoteOptionsToken) newToken;
                        logger.log(Level.INFO, "Reading a Vote Option Token...");
                        options.addAll(vote.get_options());

                        myVote = ownDecision();

                        peerConnectionStart();
                        this.startListening();


                    } else {
                        logger.log(Level.INFO, "Empty");
                    }

                    /**
                     * TODO: Implement restart message
                     */
//                    else if(newToken instanceof )


                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String sendOutcome(){
        List<Long> portsCopy = new ArrayList<>(ports);
        portsCopy.add(LISTENING_PORT);
        String allPorts = portsCopy
                .stream()
                .map(Object::toString)
                .collect(Collectors.joining(" "));

        return MessageFormat.format("OUTCOME {0} {1}",outcome,allPorts);
    }

    private void peerConnectionStart(){
        List<Long> portsCopy = new CopyOnWriteArrayList<>(ports);
        new Thread(() -> {
            while (peers.size()<ports.size()){
                portsCopy.forEach(port -> {
                    Socket peer = null;
                    try {
                        peer = new Socket("localhost", port.intValue());
                        PeerWriteThread client = new PeerWriteThread(peer);
                        peers.offer(client);
                        portsCopy.remove(port);

                    } catch (IOException e) {
//                    e.printStackTrace();
                        logger.log(Level.INFO,"Error connecting to port {0} ...",port.intValue());
                    }


                });
            }

            peers.forEach(peer -> {
                peer.write(MessageFormat.format("VOTE {0} {1}",Long.toString(LISTENING_PORT),myVote));
                new Thread(peer).start();
            });
        }).start();
    }


    public void resendMessage(){
        new Thread(() -> {
                try {
                    Thread.sleep(TIMEOUT);
                    peers.forEach(peer -> {
                        peer.write(MessageFormat.format("VOTE {0} {1}",Long.toString(LISTENING_PORT),myVote));
                    });
//                    checkBufferForNewMessages.countDown();
//                    conditionLatch = new CountDownLatch(1);
//                    conditionLatch.await();
                } catch (InterruptedException e) {
//                e.printStackTrace();
                    logger.log(Level.INFO,"Could not sleep Retransmission Thread...");
                }
        }).start();

    }
}
