/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tftpudpclient;

/**
 *
 * @author Jon
 */
public class TFTPInfo {
    static final byte RRQ = 1;
    static final byte WRQ = 2;
    static final byte DATA = 3;
    static final byte ACK = 4;
    static final byte ERROR = 5;
    static final byte[] octet = "octet".getBytes();
    static final byte zeroByte = 0;
    
    static byte[] createReadOrWriteTFTPHeader(byte opCode, String file) {
        byte[] fileInBytes = file.getBytes();
        
        byte[] tftpHeader = new byte[2 + fileInBytes.length + 1 + octet.length + 1];
        
        int counter = 0;
        tftpHeader[counter] = zeroByte;
        counter++;
        tftpHeader[counter] = opCode;
        counter++;
        for(byte fileByte : fileInBytes) {
            tftpHeader[counter] = fileByte;
            counter++;
        }
        tftpHeader[counter] = zeroByte;
        counter++;
        for(byte modeByte : octet) {
            tftpHeader[counter] = modeByte;
            counter++;
        }
        tftpHeader[counter] = zeroByte;
        counter++; 
        
        return tftpHeader;
    }
    
    static byte[] createDataOrAckTFTPHeader(byte opCode, int block, byte[] data) {
        byte[] tftpHeader;
        if(opCode == 3) {
            tftpHeader = new byte[2 + 2 + data.length];
        } else {
            tftpHeader = new byte[2 + 2];
        }
        // Add opcode to header
        int counter = 0;
        tftpHeader[counter] = zeroByte;
        counter++;
        tftpHeader[counter] = opCode;
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
        if(opCode == 3) {
            for(byte dataByte : data) {
            tftpHeader[counter] = dataByte;
            counter++;
        }
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
