import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.*;


public class Store {
    private DatagramChannel channel = null;
    private DatagramSocket socket = null;
    private Selector selector = null;
    private int storePort, bankPort, contentPort;
    private String ipAddr = "127.0.0.1";
    private Map<String, String> stocks = new LinkedHashMap<String, String>();
    private ArrayList<String> stocksAL = new ArrayList<>();
    private String storeMsgSend = null;

    public Store(int storePort, String fileName, int nameServerPort) throws IOException, NumberFormatException {
        if (storePort < 0 || storePort > 65533 || nameServerPort < 0 || nameServerPort > 65533) {
            System.err.println("Invalid command line argument for Name Server");
            System.exit(1);
        }
        // connect to server and send registration request
        // prepare request message
        String registrationMsg = "R;Store;" + storePort+";"+ipAddr;
        contactServer(registrationMsg,ipAddr,nameServerPort);
        // send look up request message
        // Look up for Bank server
        String bankAddr = contactServer("L;Bank",ipAddr,nameServerPort);
        bankPort = Integer.parseInt(bankAddr.split(";")[0].trim());
        // Look up for Content server
        String contentAddr = contactServer("L;Content",ipAddr,nameServerPort);
        contentPort = Integer.parseInt(contentAddr.split(";")[0].trim());

        // read contents from txt file and store the contents to a hash map
        readFile(fileName);
        listeningForConnections(storePort, "Store server");
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
        if (message.equals("0")) {
            reply ="";
            for(int i = 0; i < stocksAL.size(); i++){
                reply += stocksAL.get(i) + "\n";
            }
        } else {
            String itemID = stocksAL.get((Integer.parseInt(message)) - 1).split(" ")[0];
            double itemPrice = Double.parseDouble(stocksAL.get((Integer.parseInt(message)) - 1).split(" ")[1]);
            String creditCard = "1234567890123456";
            storeMsgSend =itemID +" " + itemPrice + creditCard;
            String bankReply = contactServer(storeMsgSend,ipAddr,bankPort);
            if(bankReply.equals("0")){
                reply = "Transaction aborted\n";
            } else {
                try {
                    reply = contactServer(itemID, ipAddr, contentPort);
                } catch (Exception e) {
                    reply = "Transaction Aborted\n";
                }
            }
        }
        return reply;
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
    private void readFile(String fileName) {
        BufferedReader br = null;

        try {
            String sCurrentLine;
            br = new BufferedReader(new FileReader(fileName));
            int n = 0;
            while ((sCurrentLine = br.readLine()) != null) {
                String[] item = sCurrentLine.split(" ");
                stocks.put(item[0], item[1]);
                stocksAL.add(n, sCurrentLine);
                n++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null)
                    br.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
    public static void main(String[] args) throws IOException, NumberFormatException {
        if(args.length!=3){
            System.err.println("Invalid command line arguments");
            System.exit(1);
        }
        try{
            int storePort = Integer.parseInt(args[0]);
            String fileName = args[1];
            int nameServerPort = Integer.parseInt(args[2]);
            new Store(storePort,fileName,nameServerPort);
        } catch(NumberFormatException e){
            System.err.println("Invalid command line arguments");
            System.exit(1);
        }catch (FileNotFoundException e) {
            System.err.println("File Not Found!");
            System.exit(1);
        }
//        System.out.println("Please specify store server port number, stock file name and name server port number\nIN THE FORMAT\nStore Server Port number (SPACE) Stock-file name (SPACE) Name Server port number':");
//        BufferedReader stdin = new BufferedReader(
//                new InputStreamReader(System.in));
//        String userInput = stdin.readLine();
//        String input[] = userInput.split(" ");
//        if(input.length ==3) {
//            try {
//                int storePort = Integer.parseInt(input[0]);
//                String stockfile = input[1];
//                int nameServerPort = Integer.parseInt(input[2]);
//                new Store(storePort, stockfile, nameServerPort);
//            } catch (NumberFormatException e) {
//                System.err.println("Invalid command line arguments");
//                System.exit(1);
//            } catch (FileNotFoundException e) {
//                System.err.println("File Not Found!");
//                System.exit(1);
//            }
//        } else {
//            System.err.println("File Not Found!");
//            System.exit(1);
//        }
    }

}
