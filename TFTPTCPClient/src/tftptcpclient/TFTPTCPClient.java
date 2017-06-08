package tftptcpclient;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class TFTPTCPClient {

    // used to check opcodes of incoming packets
    static final byte DATA = 3;
    static final byte ERROR = 5;
    // used for various parts of creating packets
    static final byte[] octet = "octet".getBytes();
    static final byte zeroByte = 0;

    public static void main(String[] args) throws IOException, InterruptedException {
        // used for extracting the filename from RRQ/WRQ packets
        String filename;
        // stores the user given request (read or write) as a byte
        byte opcode;
        // client socket
        Socket clientSocket;
        // used for creating tftp packets to be sent to the client
        byte[] tftpHeader;
        // trivial value used for block numbers (not required to check for tcp)
        int blockNum = 0;
        // we get an OutputStream (to send messages to the server through TCP)
        OutputStream out;

        // check that both required input arguments are passed.
        if (args.length != 2) {
            System.err.println("Incorrect Args!");
            System.exit(1);
        }

        // assign the second argument to the filename variable
        filename = args[1];

        try {
            // initialise the client/slave socket, connecting it to the server/master socket
            clientSocket = new Socket("127.0.0.1", 10000);

            // reads a byte from the server so that the client knows the server is ready to start receiving sequences of bytes
            InputStream tempIn = clientSocket.getInputStream();
            tempIn.read();

            // we get an OutputStream from the client socket (to send messages to the server through TCP)
            out = clientSocket.getOutputStream();

            // checks whether the user wants to read (1) or write (2)
            switch (args[0]) {
                
                case "1":
                    opcode = 1;
                    // we get an InputStream from the client socket (to receive messages from the server through TCP) 
                    InputStream in = clientSocket.getInputStream();
                    byte[] recvBuf;
                    // create a file to save the data
                    FileOutputStream RRQoutputStream = new FileOutputStream(filename);
                    // send the RRQ packet to the server
                    tftpHeader = createReadOrWriteTFTPHeader(opcode, filename);
                    out.write(tftpHeader);
                    // keep accepting data until the last sequence of data has been received
                    boolean notLastDataSeq = true;
                    
                    while (notLastDataSeq) {
                        
                        recvBuf = new byte[516];
                        int bytesRead = in.read(recvBuf);
                        
                        switch (recvBuf[1]) {
                            
                            case (DATA):
                                // if an empty data sequence is received, leave the switch and while loop as all data has already been received and written
                                if (bytesRead <= 4) {
                                    notLastDataSeq = false;
                                    break;
                                }

                                // Get just the data from the received byte sequence (i.e. remove opcode and block number)
                                recvBuf = Arrays.copyOfRange(recvBuf, 4, bytesRead); 
                                RRQoutputStream.write(recvBuf);

                                // leave the while loop if the last sequence of data has been written to the file
                                if (bytesRead < 516) {
                                    notLastDataSeq = false;
                                }
                                break;
                                
                            case (ERROR):
                                System.out.println("The file cannot be found on the server!");
                                Path p = Paths.get(filename);
                                RRQoutputStream.close();
                                // Delete the file that was created on the client, as nothing will be read into it (as the file could not be found on the server)
                                Files.delete(p);
                                // Leave the while loop and jump straight to the socket.close() method
                                notLastDataSeq = false;
                                break;
                        }
                    }
                    break;
                    
                case "2":
                    opcode = 2;
                    FileInputStream WRQinputStream = null;
                    try {
                        WRQinputStream = new FileInputStream(filename);
                    } catch (FileNotFoundException e) {
                        // send back an error packet if the file cannot be found
                        System.out.println("The file cannot be found on the client!");
                        tftpHeader = createErrorTFTPHeader();
                        out.write(tftpHeader);
                        break;
                    }
                    // send the WRQ packet to the server
                    tftpHeader = createReadOrWriteTFTPHeader(opcode, filename);
                    out.write(tftpHeader);
                    byte[] buf;
                    int bytesRead;
                    // keep reading from the file until the stream is at the end of the file
                    boolean fileNotFullyRead = true;
                    
                    while (fileNotFullyRead) {
                        
                        buf = new byte[512];
                        bytesRead = WRQinputStream.read(buf);
                        // if there is nothing left in the file, send an empty data sequence to the server (who will process it accordingly)
                        if (bytesRead == -1) {
                            bytesRead = 0;
                        }
                        // leave the while loop if you know the next read will have nothing in it
                        if (bytesRead < 512) {
                            fileNotFullyRead = false;
                        }
                        // create an array that removes empty spaces from buf array (if there are any), then send the data to the server
                        byte[] dataOnlyBuf = Arrays.copyOfRange(buf, 0, bytesRead);
                        blockNum = (blockNum + 1) % 65536;
                        tftpHeader = createDataTFTPHeader(blockNum, dataOnlyBuf);
                        out.write(tftpHeader);
                    }
                    break;
                default:
                    System.out.println("Incompatible Args!");
                    return;
            }
            // waiting for the case where the file is not found; server must receive error packet before the client socket closes
            Thread.sleep(500);
            // close the socket (effectively tearing down the TCP connection)
            clientSocket.close();
        } catch (IOException | InterruptedException e) {
            System.err.println("error");
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
