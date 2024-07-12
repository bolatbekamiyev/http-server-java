import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

public class ClientHandler implements Runnable {

    private final BufferedReader clientIn;
    private final OutputStream clientOut;
    private static final ArrayList<ClientHandler> clientHandlers = new ArrayList<>();
    private final String directory;

    public ClientHandler(Socket socket, String directory) {
        try {
            this.clientIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.clientOut = socket.getOutputStream();
            this.directory = directory;
            clientHandlers.add(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            String input = clientIn.readLine();
            if (input == null || input.isEmpty()) {
                return;
            }

            String[] requestParts = input.split(" ");
            String method = requestParts[0];
            String path = requestParts[1];

            switch (method) {
                case "GET":
                    handleGetRequest(path);
                    break;
                case "POST":
                    handlePostRequest(path);
                    break;
                default:
                    sendResponse("HTTP/1.1 405 Method Not Allowed\r\n\r\n");
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleGetRequest(String path) throws IOException {
        if (path.equals("/")) {
            sendResponse("HTTP/1.1 200 OK\r\n\r\n");
        } else if (path.startsWith("/echo/")) {
            String message = path.substring(6);
            clientIn.readLine();
            String encodingType = clientIn.readLine();
            if (encodingType.startsWith("Accept-Encoding: ")) {
                String[] encodings = encodingType.substring(17).split(", ");
                for (String encoding : encodings) {
                    if (encoding.equals("gzip")) {
                        encodingType = encoding;
                    }
                }
            }
            if (encodingType.equals("gzip")) {
                ByteArrayOutputStream obj = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(obj);
                gzip.write(message.getBytes(StandardCharsets.UTF_8));
                gzip.close();
                sendResponse(String.format("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n", obj.size()));
                clientOut.write(obj.toByteArray());
                clientOut.flush();
            }
            else {
                sendResponse(String.format("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n%s", message.length(), message));
            }
        } else if (path.equals("/user-agent")) {
            clientIn.readLine(); // Skip the blank line after request headers
            String userAgentLine = clientIn.readLine();
            if (userAgentLine != null && userAgentLine.startsWith("User-Agent: ")) {
                String userAgent = userAgentLine.substring(12);
                sendResponse(String.format("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n%s", userAgent.length(), userAgent));
            } else {
                sendResponse("HTTP/1.1 400 Bad Request\r\n\r\n");
            }
        } else if (path.startsWith("/files/")) {
            String fileName = path.substring(7);
            Path filePath = Paths.get(directory, fileName);
            if (Files.exists(filePath)) {
                byte[] fileBytes = Files.readAllBytes(filePath);
                sendResponse(String.format("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: %d\r\n\r\n", fileBytes.length));
                clientOut.write(fileBytes);
                clientOut.flush();
            } else {
                sendResponse("HTTP/1.1 404 Not Found\r\n\r\n");
            }
        } else {
            sendResponse("HTTP/1.1 404 Not Found\r\n\r\n");
        }
    }

    private void handlePostRequest(String path) throws IOException {
        if (path.startsWith("/files/")) {
            String fileName = path.substring(7);
            Path filePath = Paths.get(directory, fileName);

            // Ensure the directory exists
            Files.createDirectories(filePath.getParent());

            // Read the content length and content type headers
            int contentLength = 0;
            String contentLengthLine = clientIn.readLine();
            while (!contentLengthLine.isEmpty()) {
                if (contentLengthLine.startsWith("Content-Length: ")) {
                    contentLength = Integer.parseInt(contentLengthLine.substring(16));
                }
                contentLengthLine = clientIn.readLine();
            }

            // Read the file content
            char[] content = new char[contentLength];
            clientIn.read(content, 0, contentLength);

            // Write the content to the file
            Files.write(filePath, new String(content).getBytes());

            sendResponse("HTTP/1.1 201 Created\r\n\r\n");
        } else {
            sendResponse("HTTP/1.1 404 Not Found\r\n\r\n");
        }
    }

    private void sendResponse(String response) throws IOException {
        clientOut.write(response.getBytes());
        clientOut.flush();
    }
}
