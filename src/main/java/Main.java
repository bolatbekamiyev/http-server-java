import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {

    public ServerSocket serverSocket;
    public static String directory = "";

    public Main(ServerSocket serverSocket){
        this.serverSocket = serverSocket;
    }

    public void startServer(String directory) {
        try {
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept(); // Wait for connection from client.
                ClientHandler clientHandler = new ClientHandler(clientSocket, directory);
                Thread thread = new Thread(clientHandler);
                thread.start();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        if ((args.length == 2) && (args[0].equals("--directory"))) {
            directory = args[1];
        }
        ServerSocket serverSocket = new ServerSocket(4221);
        serverSocket.setReuseAddress(true);
        Main main = new Main(serverSocket);
        main.startServer(directory);

    }
}
