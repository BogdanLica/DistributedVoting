import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
public class MessageToken {

    /**
     * Parses requests.
     */
    Token getToken(String req) {
        StringTokenizer sTokenizer = new StringTokenizer(req);
        if (!(sTokenizer.hasMoreTokens()))
            return null;
        String firstToken = sTokenizer.nextToken();
        if (firstToken.equals("JOIN")) {
            if (sTokenizer.hasMoreTokens())
                return new JoinToken(req, sTokenizer.nextToken());
            else
                return null;
        }
        if (firstToken.equals("DETAILS")) {
            List<String> ports = new ArrayList<>();
            while (sTokenizer.hasMoreTokens())
                ports.add(sTokenizer.nextToken());
            return new DetailsToken(req, ports);
        }
        if (firstToken.equals("VOTE_OPTIONS")) {
            List<String> options = new ArrayList<>();
            while (sTokenizer.hasMoreTokens())
               options.add(sTokenizer.nextToken());
            return new VoteOptionsToken(req, options);
        }
        if(firstToken.equals("OUTCOME")){
            if(sTokenizer.hasMoreTokens()){
                String result = sTokenizer.nextToken();
                List<String> ports = new ArrayList<>();
                while (sTokenizer.hasMoreTokens()){
                    ports.add(sTokenizer.nextToken());
                }
                return new OutcomeToken(result,ports);
            }
            return null;
        }
        if(firstToken.equals("VOTE")){
            List<String> ports = new ArrayList<>();
            List<String> outcomes = new ArrayList<>();
            while (sTokenizer.hasMoreTokens())
            {
                ports.add(sTokenizer.nextToken());
                if(sTokenizer.hasMoreTokens()){
                    outcomes.add(sTokenizer.nextToken());
                }
            }

            return new VoteToken(ports,outcomes);


        }

        return null; // Ignore request..
    }


    /**
     * Syntax: JOIN port;
     */
    class JoinToken extends Token{
        private int _port;
        public JoinToken(String req, String port) {

            this._req=req;
            this._port= Integer.parseInt(port);

        }

        public int get_port(){
            return _port;
        }
    }


    /**
     * Syntax: DETAILS [port]
     */
    class DetailsToken extends Token{
        private List<Long> _ports = new ArrayList<>();
        DetailsToken(String req, List<String> ports){
            this._req = req;
            ports.forEach( port -> this._ports.add(Long.parseLong(port)));
        }


        public List<Long> get_ports(){
            return _ports;
        }

    }


    /**
     * Syntax: VOTE_OPTION [(options)]
     */
    class VoteOptionsToken extends Token{
        private List<String> _options;
        public VoteOptionsToken(String req, List<String> options) {
            this._req = req;
            this._options = options;
        }


        public List<String> get_options(){
            return _options;
        }
    }


    /**
     * Syntax: OUTCOME outcome [port]
     * outcome the result of the votes
     */
    class OutcomeToken extends Token{
        private List<Long> _ports = new ArrayList<>();

        public OutcomeToken(String result, List<String> ports) {
            this._req = result;
            ports.forEach( port -> {
                this._ports.add(Long.parseLong(port));
            });
        }


        public String getOutcome(){
            return this._req;
        }


        public List<Long> get_ports(){
            return this._ports;
        }



    }

    /**
     * Syntax: VOTE [port] [vote]
     * port is the sender's port number
     * vote is the option chosen
     */
    class VoteToken extends Token{
        private List<Long> _ports = new ArrayList<>();
        private List<String> _outcomes = new ArrayList<>();
        public VoteToken(List<String> ports, List<String> outcome) {
            this._outcomes=outcome;

            ports.forEach(port -> this._ports.add(Long.parseLong(port)));
        }

        public List<String> get_outcome(){
            return _outcomes;
        }


        public List<Long> get_ports(){
            return _ports;
        }
    }

    /**
     * The Token Prototype.
     */
    abstract class Token {
        String _req;
    }

}

