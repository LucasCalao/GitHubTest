/**
 * Redes Integradas de Telecomunicacoes
 * MIEEC/MEEC 2022/2023
 *
 * UnicastDaemon.java
 *
 * Thread that handles the Unicast socket events
 *
 * Updated on August 26, 2022
 * @author  Luis Bernardo
 */
package router;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/**
 *
 * @author lflb2
 */
public class UnicastDaemon extends Thread {
    volatile boolean keepRunning= true;
    DatagramSocket ds;
    Router router;
    
    // Constructor
    UnicastDaemon(Router router, DatagramSocket ds) {
        this.router= router;
        this.ds= ds;
    }

    
    // Thread main function
    @Override
    public void run() {
        byte [] buf= new byte[8096];
        DatagramPacket dp= new DatagramPacket(buf, buf.length);
        try {
            while (keepRunning) {
                try {
                    ds.receive(dp);
                    ByteArrayInputStream BAis= 
                        new ByteArrayInputStream(buf, 0, dp.getLength());
                    DataInputStream dis= new DataInputStream(BAis);
                    System.out.println("Received packet ("+dp.getLength()+
                        ") from " + dp.getAddress().getHostAddress() +
                        ":" +dp.getPort());

                    router.process_packet(dp, dis);
                }
                catch (SocketException se) {
                    if (keepRunning) {
                        router.Log("recv UDP SocketException : " + se + "\n");
                    }
                }
            }
        }
        catch(IOException e) {
            if (keepRunning) {
                router.Log("IO exception receiving data from socket : " + e);
            }
        }
    }

    
    // Stop thread
    public void stopRunning() {
        keepRunning= false;
    }
}

