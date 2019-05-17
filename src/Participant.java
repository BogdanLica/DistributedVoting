import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class Participant {
    private Long LISTENING_PORT;
    private int COORDINATOR_PORT;
    private int TIMEOUT;
    private int failCond;
    private AtomicBoolean sendOutcomeReady = new AtomicBoolean(false);
    private AtomicBoolean wait = new AtomicBoolean(false);
    private Queue<Long> ports = new ConcurrentLinkedQueue<>();
    private Queue<PeerWriteThread> peers = new ConcurrentLinkedQueue<>();
    private List<String> options = new CopyOnWriteArrayList<>();
    private volatile String outcome;
    private volatile String myVote;
    private ListenThread listenConnection;
    private ServerSocket listen = null;


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

    /**
     * Start to listen for other participants to connect to my port
     */
    private void startListening(){

        try {
            listen = new ServerSocket(LISTENING_PORT.intValue());
            listenConnection =  new ListenThread(TIMEOUT,ports.size(),failCond);

            ServerSocket finalListen = listen;
            new Thread( () -> {
                while (listenConnection.getConnectedClients()<ports.size()){
                    try {

                        Socket socket = Objects.requireNonNull(finalListen).accept();
                        listenConnection.addClient(socket);
                    } catch (IOException e) {
                    }

                }
                new Thread(listenConnection).start();
                new Thread(this::setOutcome).start();



            }).start();





        } catch (IOException e) {
        }


    }


    /**
     * Check if all the votes have been received or if new messages need to be sent
     * to the other participants as a new round has started
     */
    private void setOutcome(){
        while (true){
            if (!wait.get()){
                if (!sendOutcomeReady.get()){
                    if (listenConnection.isReady()){

                        Map<Long,String> votes = listenConnection.getVotes();
                        String v =  votes.entrySet()
                                .stream()
                                .map(e -> e.getKey() + " " + e.getValue())
                                .collect(Collectors.joining(" "));

                        peers.forEach(peer -> {
                            peer.write("VOTE " + v);
                        });



                        String message = "Computing decision...";
                        System.out.println(message);


                        message = MessageFormat.format("My vote was {0}...",myVote);
                        System.out.println(message);


                        Map<String,Integer> result = new HashMap<>();
                        if(!votes.containsKey(LISTENING_PORT))
                        {
                            result.put(myVote,1);
                        }

                        votes.values()
                                .forEach(voteToken -> {
                                    if(result.containsKey(voteToken)){
                                        int tmp = result.get(voteToken);
                                        tmp++;
                                        result.put(voteToken,tmp);
                                    }
                                    else {
                                        result.put(voteToken,1);
                                    }
                                });

                        message = MessageFormat.format("The votes received were {0}...",votes.values());
                        System.out.println(message);




                        outcome = decideOutcome(result);
                        listenConnection.stopListening();
                        sendOutcomeReady.set(true);

                    }
                    else if(listenConnection.newVotesNotification()){
                        System.out.println("New round started....");
                        if(failCond == 1){
                            listenConnection.abort();
                            propagateNewMessages(listenConnection);
                            System.exit(0);
                            break;
                        }
                        else {
                            propagateNewMessages(listenConnection);
                        }


                    }
                }
            }

        }

    }

    /**
     * For a new round, send the messages received in the previous round to all connected participants
     * @param connection the manager for all the readers
     */
    private void propagateNewMessages(ListenThread connection){

        Map<String,String> votesThisRound = connection.sendNewVotes();

        peers.forEach(peer -> {
            String message =  votesThisRound.entrySet()
                    .stream()
                    .map(e -> e.getKey() + " " + e.getValue())
                    .collect(Collectors.joining(" "));



            peer.write("VOTE " + message);
        });

    }

    /**
     * Decide on my own vote
     * @return the vote option choosen
     */
    private String ownDecision() {
        int index = ThreadLocalRandom.current().nextInt(options.size());

        return options.get(index);
    }


    /**
     * Based on the votes, decide on outcome
     * @param result the votes received
     * @return the vote options with the most votes or null if TIE
     */
    private String decideOutcome(Map<String,Integer> result){
        int max = result.values().stream().max(Comparator.naturalOrder()).get();
        List<String> keysWithMaxValues = result.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return (keysWithMaxValues.size() > 1) ? "null" : keysWithMaxValues.get(0);

    }


    /**
     * Start the connection with the Coordinator
     */
    private void contactCoordinator() {

        try {
            Socket coordinator = new Socket("localhost", COORDINATOR_PORT);
            MessageToken msg = new MessageToken();

            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(coordinator.getOutputStream()));
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(coordinator.getInputStream()));


            String line = null;

            out.write(MessageFormat.format("JOIN {0}", Long.toString(this.LISTENING_PORT)));
            out.newLine();
            out.flush();
            System.out.println("A Join Token was just sent to the server...");
            AtomicBoolean restartVote = new AtomicBoolean(false);

            new Thread( () -> {
                while (!restartVote.get()){
                    if(sendOutcomeReady.get()){

                        if(failCond == 2)
                        {
                            closeParticipantConnections();
                            System.exit(0);
                        } else {
                            try {
                                this.wait.set(true);
                                String tmp = sendOutcome();
                                System.out.println(tmp);
                                out.write(tmp);
                                out.newLine();
                                out.flush();
                                sendOutcomeReady.set(false);
                                System.out.println("Sending the Outcome to the server...");

                                if(!outcome.equals("null")){
                                    System.exit(0);
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
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
                        System.out.println("Reading a Details Token...");


                    } else if (newToken instanceof MessageToken.VoteOptionsToken) {
                        MessageToken.VoteOptionsToken vote = (MessageToken.VoteOptionsToken) newToken;
                        System.out.println("Reading a Vote Option Token...");
                        options.clear();
                        options.addAll(vote.get_options());

                        myVote = ownDecision();


                        if(peers.size() == 0){
                            peerConnectionStart();
                            this.startListening();
                        }
                        // already connected
                        else{

                            peers.forEach(peer -> {
                                peer.write(MessageFormat.format("VOTE {0} {1}", Long.toString(LISTENING_PORT), myVote));
                            });

                            listenConnection.startListening();
                            sendOutcomeReady.set(false);
                            this.wait.set(false);
                        }

                    }


                }

            }

        } catch (IOException e) {
        }
    }

    /**
     * Shutdown the connection with the other participants
     */
    private void closeParticipantConnections() {
        listenConnection.shutdownReaders();
        for (PeerWriteThread peer : peers) {
            peer.shutdown();
        }
        try {
            listen.close();
        } catch (IOException e) {
            System.out.println("Closing my listening port");
        }
    }

    /**
     * Get the string representation of the outcome
     * @return the outcome as a String
     */
    private String sendOutcome(){
        List<Long> portsCopy = new ArrayList<>(ports);
        portsCopy.add(LISTENING_PORT);
        String allPorts = portsCopy
                .stream()
                .map(Object::toString)
                .collect(Collectors.joining(" "));

        return MessageFormat.format("OUTCOME {0} {1}",outcome,allPorts);
    }

    /**
     * Initiate the peer to peer connection and after all the connections are established,
     * send my vote option
     */
    private void peerConnectionStart(){
        List<Long> portsCopy = new CopyOnWriteArrayList<>(ports);
        new Thread(() -> {
            // try to connect to clients until they are up
            while (peers.size()<ports.size()){
                portsCopy.forEach(port -> {
                    Socket peer = null;
                    try {
                        peer = new Socket("localhost", port.intValue());
                        PeerWriteThread client = new PeerWriteThread(peer);
                        peers.offer(client);
                        portsCopy.remove(port);

                    } catch (IOException e) {
                    }


                });
            }

            peers.forEach(peer -> {
                new Thread(peer).start();
            });

            // fail during step 4
            if(failCond == 1){
                int skipElements = ThreadLocalRandom.current().nextInt(1,peers.size());

                peers.stream()
                        .skip(skipElements)
                        .forEach(peer -> {
                            peer.write(MessageFormat.format("VOTE {0} {1}",Long.toString(LISTENING_PORT),myVote));
                        });

                System.out.println(MessageFormat.format("I skipped {0} participants",skipElements));

            }
            else {
                peers.forEach(peer -> {
                    peer.write(MessageFormat.format("VOTE {0} {1}",Long.toString(LISTENING_PORT),myVote));
                });
            }

        }).start();
    }
}
