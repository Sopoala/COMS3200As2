import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by RandyZhongbin on 4/25/2015.
 */
public class Bank {
    private String ipAddr = "127.0.0.1";
    private long itemID = 0;
    private Selector selector = null;
    private DatagramChannel channel = null;
    private DatagramSocket socket = null;
    public Bank(int bankPort, int nameServerPort) throws IOException{
        if (bankPort < 0 || bankPort > 65533 || nameServerPort < 0 || nameServerPort > 65533){
            System.err.println("Invalid command line arguments for Bank Server");
            System.exit(1);
        }
        // Prepare registration message
        String request = "R;Bank;" + bankPort +";"+ipAddr;
        // Register with name server
        contactServer(request,ipAddr,nameServerPort);
        // Listening for incoming connections
        listeningForConnections(bankPort, "Bank Server");
    }

    private String contactServer(String msg, String ipAddr, int serverPort) {
        DatagramChannel channel = null;
        String reply = null;
        try{
            channel = DatagramChannel.open();
            // set Blocking mode to non-blocking
            channel.configureBlocking(false);
            // set Server info
            SocketAddress sa = new InetSocketAddress(ipAddr, serverPort);
            // open selector
            Selector selector = Selector.open();
            // connect to Server
            channel.connect(sa);
            System.out.println("Bank is connected to name server");
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            // registers this channel with the given selector, returning a selection key
            channel.register(selector, SelectionKey.OP_WRITE);
            while (selector.select() > 0) {
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                for (SelectionKey selectionKey : selectedKeys) {
                    // test connectivity
                    if (selectionKey.isReadable()) {
                        DatagramChannel dc = (DatagramChannel) selectionKey.channel();
                        dc.read(buffer);
                        buffer.flip();

                        reply = Charset.forName("UTF-8").decode(buffer).toString();
                        System.out.println(reply);
                        buffer.clear();

                        // set register status to WRITE
                        dc.register(selector, SelectionKey.OP_WRITE);
                        selector.close();
                    }
                    // test whether this key's channel is ready for writing to Server
                    else if (selectionKey.isWritable()) {
                        DatagramChannel dc = (DatagramChannel) selectionKey.channel();
                        dc.write(Charset.forName("UTF-8").encode(msg.trim()));
                        dc.register(selector, SelectionKey.OP_READ);
                    }
                }
                if (!selector.isOpen()) {
                    break;
                }
            }
            return reply;
        } catch (IOException e) {
            e.printStackTrace();
            return "Error";
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
        String[] splitMsg = message.split(" ");
        itemID = Long.parseLong(splitMsg[0].trim());
        if(itemID % 2 == 1 ){
            reply = "1";
            System.out.println("\n" + itemID + " OK");
        } else {
            reply = "0";
            System.out.println("\n" + itemID + " NOT OK");
        }
        return reply;
    }
    public static void main(String[] args) throws IOException, NumberFormatException{
        if(args.length!=2){
            System.err.println("Invalid command line arguments");
            System.exit(1);
        }
        try{
            int bankPort = Integer.parseInt(args[0]);
            int nameServerPort = Integer.parseInt(args[1]);
            new Bank(bankPort,nameServerPort);
        } catch(NumberFormatException e){
            System.err.println("Invalid command line arguments");
            System.exit(1);
        }
//        System.out.println("Please specify bank server port and name server port it will connect to, using space");
//        BufferedReader stdin = new BufferedReader(
//                new InputStreamReader(System.in));
//        String userInput = stdin.readLine();
//        String input[] = userInput.split(" ");
//        if(input.length ==2) {
//            try {
//                int bankPort = Integer.parseInt(input[0]);
//                int nameServerPort = Integer.parseInt(input[1]);
//                new Bank(bankPort, nameServerPort);
//            } catch (NumberFormatException e) {
//                System.err.println("Invalid command line arguments");
//                System.exit(1);
//            }
//        } else {
//            System.err.println("Invalid command line arguments");
//            System.exit(1);
//        }
    }
}
