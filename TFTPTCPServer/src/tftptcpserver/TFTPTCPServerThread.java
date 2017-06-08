package tftptcpserver;

import java.net.*;
import java.io.*;
import java.util.Arrays;

public class TFTPTCPServerThread extends Thread {
    // used to check opcodes of incoming packets
    static final byte RRQ = 1;
    static final byte WRQ = 2;
    static final byte DATA = 3;
    static final byte ERROR = 5;
    // used for extracting the filename from RRQ/WRQ packets
    int i;
    String filename;
    // client socket
    private Socket slaveSocket = null;
    // used for various parts of creating packets
    static final byte[] octet = "octet".getBytes();
    static final byte zeroByte = 0;
    // used for creating tftp packets to be sent to the client
    byte[] tftpHeader;
    // trivial value used for block numbers (not required to check for tcp)
    int blockNum = 1;

    public TFTPTCPServerThread(Socket socket) {
        super("MTTCPServerThread");
        this.slaveSocket = socket;
    }

    @Override
    public void run() {

        // All Input/Output from/to the socket will take place through the input/output streams defined below.
        OutputStream socketOutput;
        InputStream socketInput;

        try {
            // We get an OutputStream from the slave socket (to send messages to the client through TCP)
            socketOutput = slaveSocket.getOutputStream();

            // We get an InputStream from the slave socket (to receive messsages from the client through TCP)
            socketInput = slaveSocket.getInputStream();

            // Write performed to tell the client the server is ready to start accepting sequences of bytes
            socketOutput.write(2);

            byte[] recvBuf = new byte[516];
            socketInput.read(recvBuf);

            switch (recvBuf[1]) {
                case (RRQ):
                    System.out.println("read request received");
                    FileInputStream RRQinputStream;
                    byte[] buf = new byte[512];
                    int bytesRead;
                    i = 2;
                    filename = "";
                    // extracts filename from RRQ packet
                    while (recvBuf[i] != zeroByte) {
                        byte[] singleByte = {recvBuf[i]};
                        filename = filename.concat(new String(singleByte));
                        i++;
                    }
                    try {
                        RRQinputStream = new FileInputStream(filename);
                    } catch (FileNotFoundException e) {
                        // send back an error packet if the file cannot be found
                        System.out.println("The file cannot be found on the server!");
                        tftpHeader = createErrorTFTPHeader();
                        socketOutput.write(tftpHeader);
                        break;
                    }
                    // keep reading from the file until the stream is at the end of the file
                    boolean fileNotFullyRead = true;
                    while (fileNotFullyRead) {
                        bytesRead = RRQinputStream.read(buf);
                        // if there is nothing left in the file, send an empty data packet to the client (who will process it accordingly)
                        if (bytesRead == -1) {
                            bytesRead = 0;
                        }
                        // leave the while loop if you know the next read will have nothing in it
                        if (bytesRead < 512) {
                            fileNotFullyRead = false;
                        }
                        // create an array that removes empty spaces from buf array (if there are any), then send the data to the client
                        byte[] dataArray = Arrays.copyOfRange(buf, 0, bytesRead);
                        tftpHeader = createDataTFTPHeader(blockNum, dataArray);
                        blockNum = (blockNum + 1) % 65536;
                        socketOutput.write(tftpHeader);
                    }
                    break;
                case (WRQ):
                    System.out.println("write request received");
                    i = 2;
                    filename = "";
                    // extracts filename from WRQ packet
                    while (recvBuf[i] != zeroByte) {
                        byte[] singleByte = {recvBuf[i]};
                        filename = filename.concat(new String(singleByte));
                        i++;
                    }
                    FileOutputStream WRQoutputStream = new FileOutputStream(filename);
                    // keep accepting data until the last sequence of data has been received
                    boolean notLastDataSeq = true;
                    while (notLastDataSeq) {
                        recvBuf = new byte[516];
                        bytesRead = socketInput.read(recvBuf);
                        switch (recvBuf[1]) {
                            case (DATA):
                                // if an empty data packet is received, leave the switch and while loop as all data has already been received and written
                                if (bytesRead <= 4) {
                                    notLastDataSeq = false;
                                    break;
                                }

                                // Get just the data from the received byte sequence (i.e. remove opcode and block number)
                                recvBuf = Arrays.copyOfRange(recvBuf, 4, bytesRead); 
                                WRQoutputStream.write(recvBuf);

                                // leave the while loop as the last sequence of data has been written to the file
                                if (bytesRead < 516) {
                                    notLastDataSeq = false;
                                }
                                break;
                            case (ERROR):
                                System.out.println("The file cannot be found on the server!");
                                // leave the while loop and jump straight to the socket.close() method
                                notLastDataSeq = false;
                                break;
                        }
                    }
                    break;
            }

            // close the input/output streams and the client socket
            socketInput.close();
            socketOutput.flush();
            socketOutput.close();
            System.err.println("Socket Closed");
            slaveSocket.close();

        } catch (IOException e) {
            System.err.println(e);
        }
    }

    static byte[] createReadOrWriteTFTPHeader(byte opCode, String file) {
        byte[] fileInBytes = file.getBytes();

        byte[] tftpHeader = new byte[2 + fileInBytes.length + 1 + octet.length + 1];

        int counter = 0;
        tftpHeader[counter] = zeroByte;
        counter++;
        tftpHeader[counter] = opCode;
        counter++;
        for (byte fileByte : fileInBytes) {
            tftpHeader[counter] = fileByte;
            counter++;
        }
        tftpHeader[counter] = zeroByte;
        counter++;
        for (byte modeByte : octet) {
            tftpHeader[counter] = modeByte;
            counter++;
        }
        tftpHeader[counter] = zeroByte;
        counter++;

        return tftpHeader;
    }

    static byte[] createDataTFTPHeader(int block, byte[] data) {
        byte[] tftpHeader;
        tftpHeader = new byte[2 + 2 + data.length];
        // Add opcode to header
        int counter = 0;
        tftpHeader[counter] = zeroByte;
        counter++;
        byte three = 3;
        tftpHeader[counter] = three;
        counter++;
        // Add block number to the header by firstly converting int block number to 2 byte equivalent
        byte[] blockInBytes = new byte[2];
        blockInBytes[0] = (byte) (block & 0xFF);
        blockInBytes[1] = (byte) ((block >> 8) & 0xFF);
        tftpHeader[counter] = blockInBytes[0];
        counter++;
        tftpHeader[counter] = blockInBytes[1];
        counter++;
        // Add data to header, if it is a data packet
        for (byte dataByte : data) {
            tftpHeader[counter] = dataByte;
            counter++;
        }
        return tftpHeader;
    }

    static byte[] createErrorTFTPHeader() {
        byte[] errorMsg = "File Not Found".getBytes();
        byte[] tftpHeader = new byte[2 + 2 + errorMsg.length + 1];
        int counter = 0;
        // Add opcode to header
        tftpHeader[counter] = zeroByte;
        counter++;
        byte five = 5;
        tftpHeader[counter] = five;
        counter++;
        // Add error code (always 1, file not found) to header
        tftpHeader[counter] = zeroByte;
        counter++;
        byte one = 1;
        tftpHeader[counter] = one;
        counter++;
        // Add error message to header
        for (byte errorByte : errorMsg) {
            tftpHeader[counter] = errorByte;
            counter++;
        }
        tftpHeader[counter] = zeroByte;
        return tftpHeader;
    }
}
