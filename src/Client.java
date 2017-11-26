import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

/**
 * Created by Serega on 25.11.2017.
 */
public class Client {
    private final String serverAddress;
    private final int serverPort;
    private final Scanner scanner = new Scanner(System.in);
    private boolean alive;
    private DataInputStream in;
    private DataOutputStream out;
    private Socket clientSocket;


    public static void main(String[] args) {
        int serverPort = 6666;
        String serverAddress = "localhost"; // equals "127.0.0.1"
        if (args != null) {
            if (args.length >= 1 && args[0].length() > 0) {
                serverPort = Integer.valueOf(args[0]);
            }
            if (args.length >= 2 && args[1].length() > 0) {
                serverAddress = args[1];
            }
        }
        Client client = new Client(serverAddress, serverPort);
        try {
            client.init();
            client.messagingWithServerLoop();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    Client(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    private void init() throws IOException {
        System.out.println("Connecting to server " + serverAddress + " on port " + serverPort);
        clientSocket = new Socket(serverAddress, serverPort);
        InputStream sin = clientSocket.getInputStream();
        OutputStream sout = clientSocket.getOutputStream();
        in = new DataInputStream(sin);
        out = new DataOutputStream(sout);
        alive = true;
    }

    void messagingWithServerLoop() {
        while (this.alive) {
            System.out.print("Enter request: ");
            String request = scanner.nextLine();
            try {
                sendRequest(request);
                String response = getResponse();
                printServerResponse(response);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void printServerResponse(String response) {
        System.out.println("Server response:");
        String formattedResponse = Arrays.stream(response.split("\n\r"))
                .map(line -> line = "\t" + line)
                .reduce(
                        (s1, s2) -> s1 + s2
                ).orElse("No response");
        System.out.println(formattedResponse);
    }

    private String getResponse() throws IOException {
        return in.readUTF();
    }

    private void sendRequest(String request) throws IOException {
        out.writeUTF(request);
        out.flush(); // await data writing
    }

    private void close() throws IOException {
        clientSocket.close();
    }


    public int getPort() {
        return serverPort;
    }
}
