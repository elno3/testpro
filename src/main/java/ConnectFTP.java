import sun.net.www.protocol.ftp.FtpURLConnection;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Created by numannsaid on 09/06/16.
 */
public class ConnectFTP {

    private String host;
    private String username;
    private String password;

    private Socket socket = null;

    private BufferedReader reader = null;

    private BufferedWriter writer = null;

    private static boolean DEBUG = true;


    public ConnectFTP(String host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
    }



    public static void main(String[] args) throws IOException {

        ConnectFTP ftp = new ConnectFTP("192.168.0.13", "elnoa", "Mass1m0");
        //ftp.connectSocket();
        ftp.connect("192.168.0.13", 21,"elnoa", "Mass1m0");
        ftp.bin();
        boolean uploaded = ftp.stor(new File("/Users/numannsaid/Desktop/loggs.txt"));
        assert uploaded == true;
    }

    public synchronized void connect(String host, int port, String user,
            String pass) throws IOException {
        if (socket != null) {
            throw new IOException("SimpleFTP is already connected. Disconnect first.");
        }
        socket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream()));

        String response = readLine();
        if (!response.startsWith("220 ")) {
            throw new IOException(
                    "SimpleFTP received an unknown response when connecting to the FTP server: "
                            + response);
        }

        sendLine("USER " + user);

        response = readLine();
        if (!response.startsWith("331 ")) {
            throw new IOException(
                    "SimpleFTP received an unknown response after sending the user: "
                            + response);
        }

        sendLine("PASS " + pass);

        response = readLine();
        if (!response.startsWith("230 ")) {
            throw new IOException(
                    "SimpleFTP was unable to log in with the supplied password: "
                            + response);
        }

        // Now logged in.
    }

    /**
     * Disconnects from the FTP server.
     */
    public synchronized void disconnect() throws IOException {
        try {
            sendLine("QUIT");
        } finally {
            socket = null;
        }
    }

    /**
     * Returns the working directory of the FTP server it is connected to.
     */
    public synchronized String pwd() throws IOException {
        sendLine("PWD");
        String dir = null;
        String response = readLine();
        if (response.startsWith("257 ")) {
            int firstQuote = response.indexOf('\"');
            int secondQuote = response.indexOf('\"', firstQuote + 1);
            if (secondQuote > 0) {
                dir = response.substring(firstQuote + 1, secondQuote);
            }
        }
        return dir;
    }

    /**
     * Changes the working directory (like cd). Returns true if successful.
     */
    public synchronized boolean cwd(String dir) throws IOException {
        sendLine("CWD " + dir);
        String response = readLine();
        return (response.startsWith("250 "));
    }

    /**
     * Sends a file to be stored on the FTP server. Returns true if the file
     * transfer was successful. The file is sent in passive mode to avoid NAT or
     * firewall problems at the client end.
     */
    public synchronized boolean stor(File file) throws IOException {
        if (file.isDirectory()) {
            throw new IOException("SimpleFTP cannot upload a directory.");
        }

        String filename = file.getName();

        return stor(new FileInputStream(file), filename);
    }

    /**
     * Sends a file to be stored on the FTP server. Returns true if the file
     * transfer was successful. The file is sent in passive mode to avoid NAT or
     * firewall problems at the client end.
     */
    public synchronized boolean stor(InputStream inputStream, String filename)
            throws IOException {

        BufferedInputStream input = new BufferedInputStream(inputStream);

        sendLine("PASV");
        String response = readLine();
        if (!response.startsWith("227 ")) {
            throw new IOException("SimpleFTP could not request passive mode: "
                    + response);
        }

        String ip = null;
        int port = -1;
        int opening = response.indexOf('(');
        int closing = response.indexOf(')', opening + 1);
        if (closing > 0) {
            String dataLink = response.substring(opening + 1, closing);
            StringTokenizer tokenizer = new StringTokenizer(dataLink, ",");
            try {
                ip = tokenizer.nextToken() + "." + tokenizer.nextToken() + "."
                        + tokenizer.nextToken() + "." + tokenizer.nextToken();
                port = Integer.parseInt(tokenizer.nextToken()) * 256
                        + Integer.parseInt(tokenizer.nextToken());
            } catch (Exception e) {
                throw new IOException("SimpleFTP received bad data link information: "
                        + response);
            }
        }

        Socket dataSocket = new Socket(this.host, port);

        sendLine("STOR " + filename);

        response = readLine();
        if (!response.startsWith ("125 ")) {
            if (!response.startsWith("150 "))
            throw new IOException("SimpleFTP was not allowed to send the file: "
                    + response);
        }

        BufferedOutputStream output = new BufferedOutputStream(dataSocket
                .getOutputStream());
        byte[] buffer = new byte[4096];
        int bytesRead = 0;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        output.flush();
        output.close();
        input.close();

        response = readLine();
        return response.startsWith("226 ");
    }

    /**
     * Enter binary mode for sending binary files.
     */
    public synchronized boolean bin() throws IOException {
        sendLine("TYPE I");
        String response = readLine();
        return (response.startsWith("200 "));
    }

    /**
     * Enter ASCII mode for sending text files. This is usually the default mode.
     * Make sure you use binary mode if you are sending images or other binary
     * data, as ASCII mode is likely to corrupt them.
     */
    public synchronized boolean ascii() throws IOException {
        sendLine("TYPE A");
        String response = readLine();
        return (response.startsWith("200 "));
    }

    /**
     * Sends a raw command to the FTP server.
     */
    private void sendLine(String line) throws IOException {
        if (socket == null) {
            throw new IOException("SimpleFTP is not connected.");
        }
        try {
            writer.write(line + "\r\n");
            writer.flush();
            if (DEBUG) {
                System.out.println("> " + line);
            }
        } catch (IOException e) {
            socket = null;
            throw e;
        }
    }

    private String readLine() throws IOException {
        String line = reader.readLine();
        if (DEBUG) {
            System.out.println("< " + line);
        }
        return line;
    }


}
