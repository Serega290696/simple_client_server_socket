import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

public class Server {
    private final int port;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final List<ClientInputOutputWrapper> sockets = new ArrayList<>();
    private final Thread statusReporter;
    private ServerSocket serverSocket;
    private UnaryOperator<String> operator = s -> "You sent: '" + s + "'";
    private boolean exit = false;

    public static void main(String[] args) {
        int port; // from 1025 to 65535
        if (args != null && args.length > 0 && args[0].length() > 0) {
            port = Integer.valueOf(args[0]);
        } else {
            port = 6666;
        }
        Server server = new Server(port);
        try {
            server.init();
            server.awaitAndServeClient();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public Server(int port) {
        this.port = port;
        statusReporter = new Thread(() -> {
            while (!this.exit) {
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("Status. \n" +
                        "\tClients amount - "
                        + sockets.size()
                        + "(" + sockets.stream()
                        .map(s -> s.getRequestsHistory().size())
                        .reduce("",
                                (i1, i2) -> i1 + ", " + i2,
                                (s1, s2) -> s1 + s2
                        )
                        + ")\n"
                );
            }
        });

    }

    private void close() throws IOException {
        System.out.println("Start shutdown. . .");
        System.out.println("Gently close clients");
        sockets.forEach(ClientInputOutputWrapper::shutdown);
        System.out.println("Close socket server");
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        System.out.println("Gently shutdown clients processing threads. . .");
        pool.shutdown();
        try {
            pool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            System.out.println("Shutdown clients processing threads. . .");
            pool.shutdownNow();
        }
        System.out.println("Shutdown server successfully");
    }

    private String generateResponse(String line) {
        return operator.apply(line);
    }

    private void init() throws IOException {
        if (serverSocket == null) {
            serverSocket = new ServerSocket(port);
            System.out.println("Creating new server " + serverSocket.getInetAddress() + " on port " + port);
        }
        statusReporter.start();
    }

    private void awaitAndServeClient() throws IOException {
        System.out.println("Start awaiting and serving clients. . .");
        while (!this.exit) {
            Socket socket = serverSocket.accept();
            ClientInputOutputWrapper client = new ClientInputOutputWrapper(socket);
            sockets.add(client);
            System.out.println("Register new client: " + client.toString());
            pool.execute(() -> {
                while (!this.exit && client.isAlive()) {
                    String request = client.getRequest();
                    registerRequest(request, client);
                    String response = generateResponse(request);
                    client.sendResponse(response);
                }
                sockets.remove(sockets.indexOf(client));
            });
        }
    }

    private void registerRequest(String request, ClientInputOutputWrapper client) {
        System.out.println("Get request '" + request + "' from client: " + client);
        client.registerRequest(request);
    }

    class ClientInputOutputWrapper {

        private final Socket socket;
        private final DataInputStream sin;
        private final DataOutputStream sout;
        private final List<String> requestsHistory = new LinkedList<>();
        private boolean alive = true;

        ClientInputOutputWrapper(Socket socket) throws IOException {
            this.socket = socket;
            sin = new DataInputStream(socket.getInputStream());
            sout = new DataOutputStream(socket.getOutputStream());
        }

        String getRequest() {
            String line = null;
            try {
                line = sin.readUTF();
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    this.shutdown();
                    sin.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            return line;
        }

        void sendResponse(String response) {
            try {
                sout.writeUTF(response);
                sout.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void registerRequest(String request) {
            requestsHistory.add(request);
        }

        void shutdown() {
            this.alive = false;
        }

        boolean isAlive() {
            return alive;
        }

        public List<String> getRequestsHistory() {
            return requestsHistory;
        }


        public int getPort() {
            return port;
        }

        @Override
        public String toString() {
            return String.format("Client{port - %s, local address - %s, requestsHistory history size - %d}",
                    socket.getLocalPort(),
                    socket.getRemoteSocketAddress(),
                    requestsHistory.size());
        }

    }
}
