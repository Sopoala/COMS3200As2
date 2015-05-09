import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by RandyZhongbin on 4/24/2015.
 */
public class NameServer {
    //set server parameters
    private Selector selector = null;
    private DatagramChannel channel = null;
    private DatagramSocket socket = null;
    Map<String,String> registerServers = new HashMap<String,String>();
    public NameServer(int portNo){
        // check if the arguments valid
        if (portNo < 0 || portNo > 65535){
            System.err.println("Invalid command line argument for Name Server");
            System.exit(1);
        }
        listeningForConnections(portNo, "Name Server");
    }

    private void listeningForConnections(int portNo, String serverName) {
        try{
            // open selector
            selector = Selector.open();
            // open datagram channel
            channel = DatagramChannel.open();
            // set the socket associated with this channel
            socket = channel.socket();
            // set Blocking mode to non-blocking
            channel.configureBlocking(false);
            ByteBuffer buffer = ByteBuffer.allocate(1024);
           try {
               // bind port
               socket.bind(new InetSocketAddress(portNo));
               // registers this channel with the given selector, returning a selection key
               channel.register(selector, SelectionKey.OP_READ, buffer);
           } catch (BindException e){
               System.err.println("Cannot listen on the given port" + portNo);
           }
            System.out.println(serverName + " is activated, listening on port: "+ portNo);

            while(selector.select() > 0){
                for (SelectionKey key : selector.selectedKeys()) {
                    // test whether this key's channel is ready to accept a new socket connection
                    if (key.isReadable()) {
                        DatagramChannel dc = (DatagramChannel) key.channel();
                        // get allocated buffer with size 1024
                        ByteBuffer readBuffer = (ByteBuffer) key.attachment();
                        // try to read bytes from the channel into the buffer
                        SocketAddress sa = dc.receive(readBuffer);
                        System.out.print("\nConnection from " + sa.toString());
                        readBuffer.flip();
                        CharBuffer charBuffer = Charset.forName("UTF-8").decode(readBuffer);
                        readBuffer.clear();
                        String message = charBuffer.toString().trim();
                        // react by Client's message
                        String reply = reactToMessage(message);
                            List<Object> objList = new ArrayList<Object>();
                            objList.add(sa);
                            objList.add(reply);
                            // set register status to WRITE
                            dc.register(key.selector(), SelectionKey.OP_WRITE, objList);
                        }

                    // if the selection key is readable
                    else if (key.isWritable()) {
                        //System.err.println("now the key is writable and ready to send to client");
                        DatagramChannel dc = (DatagramChannel) key.channel();
                        List<?> objList = (ArrayList<?>) key.attachment();
                        SocketAddress sa = (SocketAddress) objList.get(0);
                        String reply = (String) objList.get(1);
                        ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
                        writeBuffer.put(Charset.forName("UTF-8").encode(reply));
                        writeBuffer.flip();
                        dc.send(writeBuffer, sa);
                        writeBuffer.clear();
                        // set register status to READ
                        dc.register(selector, SelectionKey.OP_READ, writeBuffer);
                    }
                }
                if (!selector.isOpen()) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String reactToMessage(String message) {
        String reply = null;
        // split the message to check the message is registration or looking up message
        String[] incomingMsg = message.split(";");
        // if the message is registration
        if ("R".equalsIgnoreCase(incomingMsg[0])) {
            // get the server name, port and ip address from the registration message
            String serverName = incomingMsg[1];
            String port = incomingMsg[2];
            String ipAddr = incomingMsg[3];
            //save server info
            try{
                // put the server registration message into registerServers hashmap
                registerServers.put(serverName, port + " ; " + ipAddr);
                reply = "Registration is successful";
            } catch (Exception e){
                // if there is something wrong when registration, print out the error message
                System.err.println("Error occurred when register with name server.");
                message = "Error.";
            }
            // if the message is Looking up server request
        } else if("L".equalsIgnoreCase(incomingMsg[0])){
            // get the server name from the request
            String serverName = incomingMsg[1];
            // check if the registerServers contains the looked up server
            if(registerServers.containsKey(serverName)){
                // construct the reply message from the hash map if the server is registered
                reply = registerServers.get(serverName);
            } else {
                // construct the reply message if the server is not registered
                reply = serverName + " is not registered with name server";
            }
        } else {
            // if other error occurs, print out the error message
            reply = "Error incoming message format";
        }
        return reply;
    }

    public static void main(String[] args)  throws IOException {
        if(args.length!=1){
            System.err.println("Invalid command line arguments");
            System.exit(1);
        }
        try{
            int portNumber = Integer.parseInt(args[0]);
            new NameServer(portNumber);
        } catch(NumberFormatException e){
            System.err.println("Invalid command line arguments");
            System.exit(1);
        }
//        System.out.println("Please specify a port no for Name Server to listen:");
//        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
//        // read user input
//        String userInput = stdin.readLine();
//        try{
//            // cast the user input to integer as the server port
//            int portNumber = Integer.parseInt(userInput);
//            new NameServer(portNumber);
//        }
//        catch(NumberFormatException e){
//            System.err.println("Invalid command line arguments");
//            System.exit(1);
//        }
    }
}
