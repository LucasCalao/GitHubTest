/**
 * Redes Integradas de Telecomunicacoes
 * MIEEC/MEEC 2022/2023
 *
 * Neighbour.java
 *
 * Holds the neighbor router internal data
 *
 * Updated on August 26, 2022
 * @author  Luis Bernardo
 */
package router;

import java.net.*;
import java.io.*;
import java.util.*;


/**
 * Holds neighbor Router internal data
 */
public final class Neighbour {
    /** neigbour's name (address) [A,Z] */       
    public char name;
    /** IP address of the Neighbour */
    public String ip;
    /** port number of the Neighbour */
    public int port;
    /** distance to the Neighbour */
    public int dist;
    /** address of the Neighbour, includes IP+port */
    public InetAddress netip;
    /** Log object */
    private Log log;
    
// Distance-vector protocols' specific data
    /** Vector received from Neighbour Router */    
    public Entry[] vec;
    /** Date when the vector was received */
    public Date vec_date;
    /** Vector TTL */
    public long vec_TTL;    // in seconds
    
    /**
     * Return the name of the Neighbour
     * @return the character with the name
     */
    public char Name() { return name; }
    /**
     * Return the IP address of the Neighbour
     * @return IP address
     */
    public String Ip() { return ip; }
    /**
     * Return the port number of the Neighbour
     * @return port number
     */
    public int Port()  { return port; }
    /**
     * Return the distance to the Neighbour
     * @return distance
     */
    public int Dist()  { return dist; }
    /**
     * Return the InetAddress object to send messages to the Neighbour
     * @return InetAddress object
     */    
    public InetAddress Netip() { return netip; }
    /** Vector-distance protocol specific function:
     *          Returns a vector, if it exists
     * @return  the vector, or null if it does not exists */
    public Entry[] Vec() { return vec_valid()? vec : null; }

    
    /**
     * Parse a string with a compact name, defining the local name
     * @param name  the string
     * @return  true if name is valid, false otherwise
     */
    private boolean parseName(String name) {
        // Clear name
        if (name.length() != 1)
            return false;
        char c= name.charAt(0);
        if (!Character.isUpperCase (c))
            return false;
        this.name= c;
        return true;
    }
    
    /**
     * Constructor - create an empty instance of neighbour
     */
    public Neighbour(Log log) {
        clear();
        this.log= log;
    }
    
    /**
     * Constructor - create a new instance of neighbour from parameters
     * @param name      neighbour's name
     * @param ip        ip address
     * @param port      port number
     * @param distance  distance
     * @param log       Log object
     */
    public Neighbour(char name, String ip, int port, int distance, Log log) {
        clear();
        this.log= log;
        this.ip= ip;
        if (test_IP()) {
            this.name= name;
            this.port= port;
            this.dist= distance;
        } else
            this.ip= null;
    }
    
    /**
     * Constructor - create a clone of an existing object
     * @param src  object to be cloned
     */
    public Neighbour(Neighbour src) {
        this.name= src.name;
        this.ip= src.ip;
        this.netip= src.netip;
        this.port= src.port;
        this.dist= src.dist;
        this.log= src.log;
    }
        
    /**
     * Update the fields of the Neighbour object
     * @param name      Neighbour's name
     * @param ip        ip address
     * @param port      port number
     * @param distance  distance
     */
    public void update_neigh(char name, String ip, int port, int distance) {
        this.ip= ip;
        if (test_IP()) {
            this.name= name;
            this.port= port;
            this.dist= distance;
        } else
            clear();
    }
    
    /**
     * Vector-distance specific function:
     *  updates last vector received from neighbor TTL in miliseconds
     * @param vec  vector
     * @param TTL  Time to Live
     * @throws java.lang.Exception Invalid Neighbour
     */
    public void update_vec(Entry[] vec, long TTL) throws Exception {
        if (!is_valid())
            throw new Exception ("Update vector of invalid neighbor");
        this.vec= vec;
        this.vec_date= new Date();  // Now
        this.vec_TTL= TTL;
    }
    
    /**
     * Clear the contents of the neigbour object
     */
    public void clear() {
        this.name= ' ';
        this.ip= null;
        this.netip= null;
        this.port= 0;
        this.dist= Router.MAX_DISTANCE;
        this.vec= null;
        this.vec_date= null;
        this.vec_TTL= 0;
    }

    /**
     * Test the IP address
     * @return true if is valid, false otherwise
     */
    private boolean test_IP() {
        try {
            netip= InetAddress.getByName(ip);
            return true;
        }
        catch (UnknownHostException e) {
            netip= null;
            return false;
        }
    }

    /**
     * Test if the Neighbour is valid
     * @return true if is valid, false otherwise
     */
    public boolean is_valid() { return (netip!=null); }
    
    /**
     * Vector-distance protocol specific: test if the vector is valid
     * @return true if is valid, false otherwise
     */
    public boolean vec_valid() { 
        return (vec!=null) && 
            ((new Date().getTime() - vec_date.getTime())<=vec_TTL*1000); 
    }
        
    /**
     * Send a packet to the Neighbour
     * @param ds  datagram socket
     * @param dp  datagram packet with the packet contents
     * @throws IOException Error sending packet
     */
    public void send_packet(DatagramSocket ds, 
                                DatagramPacket dp) throws IOException {
        try {
            dp.setAddress(this.netip);
            dp.setPort(this.port);
            ds.send(dp);
        }
        catch (IOException e) {
            throw e;
        }        
    }
    
    /**
     * Send a packet to the Neighbour
     * @param ds  datagram socket
     * @param os  output stream with the packet contents
     * @throws IOException Error sending packet
     */
    public void send_packet(DatagramSocket ds, 
                                ByteArrayOutputStream os) throws IOException {
        try {
            byte [] buffer = os.toByteArray();
            DatagramPacket dp= new DatagramPacket(buffer, buffer.length, 
                this.netip, this.port);
            ds.send(dp);
        }
        catch (IOException e) {
            throw e;
        }        
    }
    
    /**
     * Create a send a HELLO packet to the Neighbour
     * @param ds    datagram socket
     * @param win   main window object 
     * @return true if sent successfully, false otherwise
     */
    public boolean send_Hello(DatagramSocket ds, Router win) {
        // Send HELLO packet
        ByteArrayOutputStream os= new ByteArrayOutputStream();
        DataOutputStream dos= new DataOutputStream(os);
        try {
            dos.writeByte(Router.PKT_HELLO);
            // name ('letter')
            dos.writeChar(win.local_name());
            // Distance
            dos.writeInt(dist);
            send_packet(ds, os);
            win.HELLO_snt++;
            return true;
        }
        catch (IOException e) {
            log.Log("Internal error sending packet HELLO: "+e+"\n");
            return false;
        }        
    }
    
    /**
     * Create a send a BYE packet to the Neighbour
     * @param ds    datagram socket
     * @param win   main window object 
     * @return true if sent successfully, false otherwise
     */
    public boolean send_Bye(DatagramSocket ds, Router win) {
        ByteArrayOutputStream os= new ByteArrayOutputStream();
        DataOutputStream dos= new DataOutputStream(os);
        try {
            dos.writeByte(Router.PKT_BYE);
            dos.writeChar(win.local_name());
            send_packet(ds, os);
            win.BYE_snt++;
            return true;
        }
        catch (IOException e) {
            log.Log("Internal error sending packet BYE: "+e+"\n");
            return false;
        }        
    }
    
    /**
     * return a string with the Neighbour contents; replaces default function
     * @return string with the Neighbour contents
     */
    @Override
    public String toString() {
        String str= ""+name;
        if (name == ' ')
            str= "INVALID";
        return "("+name+" ; "+ip+" ; "+port+" ; "+dist+")";
    }
    
    /**
     * parses a string for the Neighbour field values
     * @param str  string with the values
     * @return true if parsing successful, false otherwise
     */
    public boolean parseString(String str) {
        StringTokenizer st = new StringTokenizer(str, " ();");
        if (st.countTokens( ) != 4)
            return false;
        try {
            // Parse name
            String _name= st.nextToken();
            if (!parseName(_name))
                return false;
            if (!_name.equals(""+name))
                return false;
            String _ip= st.nextToken();
            int _port= Integer.parseInt(st.nextToken());
            int _dist= Integer.parseInt(st.nextToken());
            update_neigh(name, _ip, _port, _dist);
            return is_valid();
        }
        catch (NumberFormatException e) {
            return false;
        }
    }
}
