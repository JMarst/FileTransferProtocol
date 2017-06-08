package tftpudpserver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Jon
 */
public class TFTPUDPServer extends TFTPInfo {

    protected DatagramSocket socket = null;
    // Server always port 9000 (since there is only ever one server)
    static int destTID = 9000;

    public TFTPUDPServer() throws SocketException {
        this("TFTPUDPServer");
    }

    public TFTPUDPServer(String name) throws SocketException {
        super(name);
        // **********************************************
        // Add a line here to instantiate a DatagramSocket for the socket field defined above.
        // Bind the socket to port 9000 (any port over 1024 would be ok as long as no other application uses it).
        // Ports below 1024 require administrative rights when running the applications.
        // Take a note of the port as the client needs to send its datagram to an IP address and port to which this server socket is bound.
        //***********************************************

        socket = new DatagramSocket(destTID);

    }

    @Override
    public void run() {

        // array for receiving data from packets (max size 516)
        byte[] recvBuf;
        // array for storing data from a FileInputStream (max size 512)
        byte[] buf;
        // must be initialised for catch block
        DatagramPacket rcvdPacket = new DatagramPacket(new byte[0], 0);
        // keeps track of the current block number
        int blockNum = 0;
        // variables used for passing to the createDataOrAckTFTPHeader() method
        byte three = 3;
        byte four = 4;
        // used for creating tftp packets to be sent to the client
        byte[] tftpHeader;
        // used as a variable throughout for sending packets
        DatagramPacket packet;
        // used for checking the received block number of the packet is the expected one
        int recvBlockNum;
        // stores the result of performing FileInputStream.read()
        int bytesRead;
        // used to extract just the data bytes from the buf array
        byte[] dataArray;
        // used to check if the last ACK as been received or not 
        boolean lastAck = false;
        // used to store the most recently sent packet in case a timeout occurs
        DatagramPacket lastSentPacket = new DatagramPacket(new byte[0], 0);
        // used to store the most recently received packet for retransmission purposes
        DatagramPacket lastRcvdPacket = new DatagramPacket(new byte[0], 0);
        // Used for converting the two bytes that represent the block number into an int
        int first;
        int second;
        // variables used to deal with timeouts
        boolean packetNotReceived;
        boolean notTimedOut;

        try {
            // Must initialize to start with; will be overwritten            
            FileInputStream RRQinputStream = null;
            FileOutputStream WRQoutputStream = null;

            // run forever
            while (true) {

                recvBuf = new byte[516];
                buf = new byte[512];
                rcvdPacket = new DatagramPacket(recvBuf, 516);
                packetNotReceived = true;
                notTimedOut = true;

                // loop used to catch when the socket times out
                while (packetNotReceived) {
                    try {
                        socket.receive(rcvdPacket);
                        // if received a duplicate, the last packet sent to the client needs to be retransmitted
                        if(rcvdPacket.equals(lastRcvdPacket)) {
                            socket.send(lastSentPacket);
                            notTimedOut = false;
                        }
                        lastRcvdPacket = rcvdPacket;
                        packetNotReceived = false;
                    } catch (SocketTimeoutException e) {
                        System.out.println("Socket timed out!");
                        socket.send(lastSentPacket);
                        notTimedOut = false;
                    }
                }

                // loopback address used as permanent address in all client/server interactions
                InetAddress address = InetAddress.getByName("127.0.0.1");

                // Variables used when received packet is a RRQ/WRQ to get filename from packet
                int i = 2;
                String filename = "";

                // leave the switch immediately and block the next incoming packet if a timeout has occured
                if (notTimedOut) {

                    // Deals with all types of packet by checking the opcode of the received packet
                    switch (recvBuf[1]) {

                        case (RRQ):
                            System.out.println("read request received");
                            socket.setSoTimeout(10000);
                            lastAck = false;
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
                                packet = new DatagramPacket(tftpHeader, tftpHeader.length, rcvdPacket.getAddress(), rcvdPacket.getPort());
                                socket.setSoTimeout(0);
                                try {
                                    socket.send(packet);
                                    lastSentPacket = packet;
                                } catch (IOException ex) {
                                    Logger.getLogger(TFTPUDPServer.class.getName()).log(Level.SEVERE, null, ex);
                                }
                                break;
                            }
                            // read first stream of data
                            bytesRead = RRQinputStream.read(buf);
                            // if there is nothing left in the file, send an empty data packet to the client (who will process it accordingly)
                            // also remove the timeout on the socket, as the server does not expect any more ACK packets from this client
                            if (bytesRead == -1) {
                                bytesRead = 0;
                                socket.setSoTimeout(0);
                            }
                            if (bytesRead < 512) {
                                lastAck = true;
                            }
                            // first block number is 1 (for the 1st data packet)
                            blockNum = 1;
                            // create an array that removes empty spaces from buf array (if there are any), then send the data to the client
                            dataArray = Arrays.copyOfRange(buf, 0, bytesRead);
                            // create and send first data packet
                            tftpHeader = createDataOrAckTFTPHeader(three, blockNum, dataArray);
                            packet = new DatagramPacket(tftpHeader, tftpHeader.length, rcvdPacket.getAddress(), rcvdPacket.getPort());
                            socket.send(packet);
                            lastSentPacket = packet;
                            break;

                        case (WRQ):
                            System.out.println("write request received");
                            socket.setSoTimeout(10000);
                            // extracts filename from RRQ packet
                            while (recvBuf[i] != zeroByte) {
                                byte[] singleByte = {recvBuf[i]};
                                filename = filename.concat(new String(singleByte));
                                i++;
                            }
                            WRQoutputStream = new FileOutputStream(filename);
                            blockNum = 0;
                            // create and send ACK 0 packet
                            tftpHeader = createDataOrAckTFTPHeader(four, blockNum, buf);
                            blockNum++;
                            packet = new DatagramPacket(tftpHeader, tftpHeader.length, rcvdPacket.getAddress(), rcvdPacket.getPort());
                            socket.send(packet);
                            lastSentPacket = packet;
                            break;

                        case (DATA):
                            // logic used to convert two bytes (i.e. the recieved block number) to an int
                            second = recvBuf[3] >= 0 ? recvBuf[3] : 256 + recvBuf[3];
                            first = recvBuf[2] >= 0 ? recvBuf[2] : 256 + recvBuf[2];
                            // this variable stores the received block number in int form
                            recvBlockNum = first | (second << 8);
                            // make sure the block number received is the expected one
                            if (recvBlockNum == blockNum) {
                                // remove the timeout on the socket, as the server does not expect any more packets from this client
                                if (rcvdPacket.getLength() < 512) {
                                    socket.setSoTimeout(0);
                                }
                                // if an empty data packet is received, leave the switch as all data has already been received and written
                                if (rcvdPacket.getLength() == 4) {
                                    break;
                                }
                                // Get just the data from the received packet (i.e. remove opcode and block number)
                                recvBuf = Arrays.copyOfRange(recvBuf, 4, rcvdPacket.getLength());
                                WRQoutputStream.write(recvBuf);
                                // create and send ack for the current data packet
                                tftpHeader = createDataOrAckTFTPHeader(four, blockNum, buf);
                                packet = new DatagramPacket(tftpHeader, tftpHeader.length, rcvdPacket.getAddress(), rcvdPacket.getPort());
                                blockNum = (blockNum + 1) % 65536;
                                socket.send(packet);
                                lastSentPacket = packet;
                            }
                            break;

                        case (ACK):
                            // logic used to convert two bytes (i.e. the recieved block number) to an int
                            second = recvBuf[3] >= 0 ? recvBuf[3] : 256 + recvBuf[3];
                            first = recvBuf[2] >= 0 ? recvBuf[2] : 256 + recvBuf[2];
                            // this variable stores the received block number in int form
                            recvBlockNum = first | (second << 8);
                            // remove the timeout on the socket, as the server does not expect any more ACK packets from this client
                            if (lastAck) {
                                socket.setSoTimeout(0);
                            } // make sure the block number received is the expected one
                            else if (recvBlockNum == blockNum) {
                                bytesRead = RRQinputStream.read(buf);
                                // if there is nothing left in the file, send an empty data packet to the client (who will process it accordingly)
                                // also remove the timeout on the socket, as the server does not expect any more ACK packets from this client
                                if (bytesRead == -1) {
                                    bytesRead = 0;
                                    socket.setSoTimeout(0);
                                }
                                if (bytesRead < 512) {
                                    lastAck = true;
                                }
                                // create an array that removes empty spaces from buf array (if there are any), then send the data to the client
                                dataArray = Arrays.copyOfRange(buf, 0, bytesRead);
                                blockNum = (blockNum + 1) % 65536;
                                tftpHeader = createDataOrAckTFTPHeader(three, blockNum, dataArray);
                                packet = new DatagramPacket(tftpHeader, tftpHeader.length, rcvdPacket.getAddress(), rcvdPacket.getPort());
                                socket.send(packet);
                                lastSentPacket = packet;
                            }
                            break;

                        case (ERROR):
                            // simply move to the receive() blocking call if an error packet is received, making sure to remove the timeout
                            System.out.println("The file cannot be found on the client!");
                            socket.setSoTimeout(0);
                            break;
                    }
                }
            }
        } catch (IOException e) {
        }
        socket.close();
    }

    public static void main(String[] args) throws IOException {
        new TFTPUDPServer().start();
        System.out.println("Server Started");
    }

}
