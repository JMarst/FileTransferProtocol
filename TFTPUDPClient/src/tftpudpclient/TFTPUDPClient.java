package tftpudpclient;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 *
 * @author Jon
 */
public class TFTPUDPClient extends TFTPInfo {

    /**
     * @param args the command line arguments
     * @throws java.net.SocketException
     * @throws java.net.UnknownHostException
     * @throws java.lang.InterruptedException
     */
    public static void main(String[] args) throws SocketException, UnknownHostException, IOException, InterruptedException {

        // check that both required input arguments are passed.
        if (args.length != 2) {
            System.out.println("Incompatible Args!");
            return;
        }

        // stores the user given request (read or write) as a byte
        byte opcode;
        // keeps track of the current block number
        int blockNum = 0;
        // variables used for passing to the createDataOrAckTFTPHeader() method
        byte three = 3;
        byte four = 4;
        // used to catch when the file is not found
        boolean fileIsFound = true;
        // loopback address used as permanent address in all client/server interactions
        InetAddress address = InetAddress.getByName("127.0.0.1");
        // array for receiving data from packets (max size 516)
        byte[] recvBuf;
        // array for storing data from a FileInputStream (max size 512)
        byte[] buf;
        // used for creating tftp packets to be sent to the server
        byte[] tftpHeader;
        // input/output streams used for reading/writing data 
        FileOutputStream RRQoutputStream;
        FileInputStream WRQinputStream;
        // used as a variable throughout for sending packets
        DatagramPacket packet;
        // these variables are used to deal with timeouts
        boolean packetNotReceived;
        boolean notTimedOut;
        // used to store the most recently sent packet in case a timeout occurs
        DatagramPacket lastSentPacket = new DatagramPacket(new byte[0], 0);
        // used to store the most recently received packet for retransmission purposes
        DatagramPacket lastRcvdPacket = new DatagramPacket(new byte[0], 0);
        // used to keep track of when the last packet has been received by the server
        boolean notLastPacket = true;
        // stores the result of performing FileInputStream.read()
        int arraySize;
        // variables used to convert received block number from two bytes to an int
        int first;
        int second;
        int recvBlockNum;

        RRQoutputStream = null;
        WRQinputStream = null;

        // create socket for client
        DatagramSocket socket;
        // choose a random sourceTID/port for the client socket
        int sourceTID = ThreadLocalRandom.current().nextInt(1025, 65535);
        socket = new DatagramSocket(sourceTID);

        // create a timeout that will be set at every receive() blocking call
        socket.setSoTimeout(10000);

        // checks whether the user wants to read (1) or write (2)
        switch (args[0]) {

            case "1":
                opcode = 1;
                RRQoutputStream = new FileOutputStream(args[1]);
                // expects data packet with block number 1 to start with
                blockNum++;
                break;

            case "2":
                opcode = 2;
                try {
                    WRQinputStream = new FileInputStream(args[1]);
                } catch (FileNotFoundException e) {
                    // send an error packet if the file cannot be found (set booleans false to jump straight to socket.close())
                    fileIsFound = false;
                    notLastPacket = false;
                    System.out.println("The file cannot be found on the client!");
                    tftpHeader = createErrorTFTPHeader();
                    packet = new DatagramPacket(tftpHeader, tftpHeader.length, address, 9000);
                    socket.send(packet);
                    lastSentPacket = packet;
                }
                break;

            default:
                System.out.println("Incompatible Args!");
                return;
        }

        if (fileIsFound) {
            // send the RRQ/WRQ packet to the server
            tftpHeader = createReadOrWriteTFTPHeader(opcode, args[1]);
            packet = new DatagramPacket(tftpHeader, tftpHeader.length, address, 9000);
            socket.send(packet);
            lastSentPacket = packet;
        }

        // leave the while loop when the last packet has been received (be it an ACK, DATA or ERROR packet)
        while (notLastPacket) {

            recvBuf = new byte[516];
            buf = new byte[512];
            DatagramPacket rcvdPacket = new DatagramPacket(recvBuf, 516);
            packetNotReceived = true;
            notTimedOut = true;

            // loop used to catch when the socket times out
            while (packetNotReceived) {
                try {
                    socket.receive(rcvdPacket);
                    // if received a duplicate, the last packet sent to the server needs to be retransmitted
                    if (rcvdPacket.equals(lastRcvdPacket)) {
                        socket.send(lastSentPacket);
                        notTimedOut = false;
                    }
                    lastRcvdPacket = rcvdPacket;
                    packetNotReceived = false;
                } catch (SocketTimeoutException e) {
                    System.out.println("Socket timed out!");
                    socket.send(lastSentPacket);
                    packetNotReceived = false;
                    notTimedOut = false;
                }
            }

            // leave the switch immediately and block the next incoming packet if a timeout has occured
            if (notTimedOut) {

                // Deals with all types of packet by checking the opcode of the received packet
                switch (recvBuf[1]) {

                    case (ACK):
                        // logic used to convert two bytes (i.e. the recieved block number) to an int
                        second = recvBuf[3] >= 0 ? recvBuf[3] : 256 + recvBuf[3];
                        first = recvBuf[2] >= 0 ? recvBuf[2] : 256 + recvBuf[2];
                        // this variable stores the received block number in int form
                        recvBlockNum = first | (second << 8);
                        // make sure the block number received is the expected one
                        if (recvBlockNum == blockNum) {
                            arraySize = WRQinputStream.read(buf);
                            // if there is nothing left in the file, send an empty data packet to the server (who will process it accordingly)
                            if (arraySize == -1) {
                                arraySize = 0;
                            }
                            // leave the while loop if you know the next read will have nothing in it
                            if (arraySize < 512) {
                                notLastPacket = false;
                            }
                            // create an array that removes empty spaces from buf array (if there are any), then send the data to the server
                            byte[] dataOnlyBuf = Arrays.copyOfRange(buf, 0, arraySize);
                            blockNum = (blockNum + 1) % 65536;
                            tftpHeader = createDataOrAckTFTPHeader(three, blockNum, dataOnlyBuf);
                            packet = new DatagramPacket(tftpHeader, tftpHeader.length, address, 9000);
                            socket.send(packet);
                            lastSentPacket = packet;
                        }
                        break;

                    case (DATA):
                        // logic used to convert two bytes (i.e. the recieved block number) to an int
                        second = recvBuf[3] >= 0 ? recvBuf[3] : 256 + recvBuf[3];
                        first = recvBuf[2] >= 0 ? recvBuf[2] : 256 + recvBuf[2];
                        // this variable stores the received block number in int form
                        recvBlockNum = first | (second << 8);
                        // make sure the block number received is the expected one
                        if (recvBlockNum == blockNum) {
                            // if an empty data packet is received, leave the switch and while loop as all data has already been received and written
                            if (rcvdPacket.getLength() <= 4) {
                                notLastPacket = false;
                                break;
                            }

                            // Get just the data from the received byte sequence (i.e. remove opcode and block number)
                            recvBuf = Arrays.copyOfRange(recvBuf, 4, rcvdPacket.getLength());
                            RRQoutputStream.write(recvBuf);

                            // leave the while loop if the last sequence of data has been written to the file
                            if (rcvdPacket.getLength() < 516) {
                                notLastPacket = false;
                            }
                            // send an ACK packet with the correct block number
                            tftpHeader = createDataOrAckTFTPHeader(four, blockNum, recvBuf);
                            blockNum = (blockNum + 1) % 65536;
                            packet = new DatagramPacket(tftpHeader, tftpHeader.length, address, 9000);
                            socket.send(packet);
                            lastSentPacket = packet;
                        }
                        break;

                    case (ERROR):
                        System.out.println("The file cannot be found on the server!");
                        Path p = Paths.get(args[1]);
                        RRQoutputStream.close();
                        // Delete the file that was created on the client, as nothing will be read into it (as the file could not be found on the server
                        Files.delete(p);
                        // Leave the while loop and jump straight to the socket.close() method
                        notLastPacket = false;
                        break;
                }
            }
        }
        // waiting for the case where the file is not found; server must receive error packet before the client socket closes
        Thread.sleep(500);
        System.out.println("client socket closed");
        socket.close();
    }

}
