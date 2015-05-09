import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Set;

/**
 * Created by RandyZhongbin on 4/25/2015.
 */
public class Client {
    private String ipAddr = "127.0.0.1";
    int storePort = 0;
    boolean storeKnown = false;
    public Client(int request, int nameServerPort){
        // if the store server is unknown, send look up request message
        if(!storeKnown){
            // Look up for Bank server
            String storeAddr = contactServer("L;Store", ipAddr, nameServerPort);
            storePort = Integer.parseInt(storeAddr.split(";")[0].trim());
            storeKnown = true;
        }
        // connect to store server
        String result = contactServer(String.valueOf(request),ipAddr,storePort);
        System.out.println(result);

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
    public static void main(String[] args) throws IOException, NumberFormatException{
        if(args.length!=2){
            System.err.println("Invalid command line arguments");
            System.exit(1);
        }
        try{
            int request = Integer.parseInt(args[0]);
            if (request > 10 ){
                System.err.println("Invalid command line arguments");
                System.exit(1);
            }
            int nameServerPort = Integer.parseInt(args[1]);
            new Client(request,nameServerPort);
        } catch(NumberFormatException e){
            System.err.println("Invalid command line arguments");
            System.exit(1);
        }
//        System.out.println("Please enter your request and the name server port");
//        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
//        String userInput;
//        while((userInput = stdin.readLine()) != null){
//            String input[] = userInput.split(" ");
//            try{
//                int request = Integer.parseInt(input[0]);
//                int nameServerPort = Integer.parseInt(input[1]);
//                new Client(request, nameServerPort);
////                userInput = stdin.readLine();
//                System.out.println("\nPlease enter your request and the name server port");
//            } catch(NumberFormatException e){
//                System.err.println("Invalid command line arguments\n");
//                System.exit(1);
//            }
//        }

    }
}
