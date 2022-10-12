/**
 * Redes Integradas de Telecomunicacoes
 * MIEEC/MEEC 2022/2023
 *
 * Routing.java
 *
 * Encapsulates the routing functions, hosting multiple instances of 
 * Routing_process objects, and handles DATA packets
 *
 * Updated on August 26, 2022
 * @author  Luis Bernardo & ????? & ?????
 */
package router;

import java.util.*;
import java.net.*;
import java.io.*;
import javax.swing.*;
import java.awt.event.*;

import java.util.TimerTask; 
import java.util.Timer; 

/**
 * Encapsulates the Routing functions, hosting multiple instances of
 Routing_process objects, and handles DATA packets
 */
public class Routing {

    /**
     * Maximum length of the Entry vector length
     */
    public final int MAX_ENTRY_VEC_LEN = 10;
    /**
     * Time added to the period to define the TTL field of the ROUTE packets
     */
    public final int TTL_ADD = 10;

    // Variables
    /**
     * Routing table object
     */
    public RoutingTable tab;

    /**
     * Local address name
     */
    private char local_name;
    /**
     * Neighbour list
     */
    private NeighbourList neig;
    /**
     * Reference to main window with GUI
     */
    private Router win;
    /**
     * Unicast datagram socket used to send packets
     */
    private DatagramSocket ds;
    /**
     * Reference to graphical Routing table object
     */
    private JTable tableObj;

    public Date lastSending;
    private javax.swing.Timer timer_announce;

    // Configuration variables
    /**
     * ROUTE sending period (ms)
     */
    private final int period;
    
    /**
     * Uses Split Horizon with Poisoned Reverse
     */
    private final boolean splitHorizon;   // If it uses Split Horizon

    /**
     * Uses Hold down
     */
    private final boolean holddown;        // If it uses Holddown

    /**
     * Hold down time [s]
     */
    private final int holddown_time;       // Holdown time

    /**
     * Create a new instance of a routing object, that encapsulates routing
     * processes
     *
     * @param local_name local address
     * @param neig Neighbour list
     * @param period ROUTE timer period
     * @param splitHorz use Split Horizon
     * @param holddwn use Hold down
     * @param holddwn_t Hold down time
     * @param win reference to main window object
     * @param ds unicast datagram socket
     * @param TabObject Graphical object with the 
     */
    public Routing(char local_name, NeighbourList neig, int period,
            boolean splitHorz, boolean holddwn, int holddwn_t,
            Router win, DatagramSocket ds, JTable TabObject) {
        this.local_name = local_name;
        this.neig = neig;
        this.period = period;
        this.splitHorizon = splitHorz;
        this.holddown = holddwn;
        this.holddown_time = holddwn_t * 1000;
        this.win = win;
        this.ds = ds;
        this.tableObj = TabObject;
        // Initialize everything
        this.timer_announce = null;
        this.tab = new RoutingTable();
        Log2("new routing(local='" + local_name + "', period=" + period
                + (splitHorizon ? ", splitHorizon" : "")
                + (holddown ? (", holddown(" + holddown_time + ")") : "") + ")");
    }

    /**
     * Starts Routing thread
     * @return true if successful
     */
    public boolean start() {
        update_routing_table();
        start_announce_timer();
        return true;
    }

    /**
     * Handle a network change notification
     * @param send_always  if true, send always the ROUTE packet
     */
    public void network_changed(boolean send_always) {
        if (win.is_sendIfChanges() || send_always) {
            update_routing_table();
            if(send_local_ROUTE()){
                reset_announce_timer();
            }
            
            // COMPLETE THIS PART OF STEP 6
            // Recalculate the table and send it if send_always or if the table changed
            // Restart the ROUTE sending timer when the ROUTE is sent to the neighbours.       
        }     
    }

    /**
     * Stop all the Routing processes and resets the Routing state
     */
    public void stop() {
        try {
            stop_announce_timer();
        
            // Clean Routing table, stopping all hold down timers
            tab.clear();

            update_routing_window();
        } catch (Exception e) {
            Log("Exception stopping the router: "+e+"\n");
        }

        local_name = ' ';
        neig = null;
        win = null;
        ds = null;
        tableObj = null;
    }
    
    /**
     * Sends a ROUTE packet with route vector to Neighbour n
     *
     * @param n Neighbour reference
     * @param vec Entry vector to send to the neighbour
     * @return true if successful, false otherwise
     */
    public boolean send_local_ROUTE_to_neighbour(Neighbour n, Entry[] vec) {
        Log2("send_local_ROUTE(" + n.Name() + ")\n");

        // Prepare and send message
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(os);
        
        try {
            dos.writeByte(Router.PKT_ROUTE);
            dos.writeChar(local_name);
            dos.writeInt(period + TTL_ADD);   // TTL value
            dos.writeInt(vec.length);
            for (Entry rt : vec) {
                rt.writeEntry(dos);
            }
            byte[] buffer = os.toByteArray();
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);

            n.send_packet(ds, dp);
            lastSending = new Date();
            win.ROUTE_snt++;
            return true;
        } catch (IOException e) {
            Log("Error sending ROUTE: " + e + "\n");
            return false;
        }
    }

    /**
     * Prepare a temporary vector to send to a neighbour router
     * @param n neighbour router
     * @return  vector 
     */
    private Entry[] prepare_vec_for_neighbour(Neighbour n) {
        // Prepare a temporary routing table aux with entries to send to the neighbour
        RoutingTable aux= tab;  // Default implementation - always use the full routing table
        
        // TO MODIFY IN STEPS 5 and 7
        // Your split horizon and hold down implementations must remove from the vector all
        //   destinations that are not accessible!
        //
        // Create a new routing table object and add only the the valid entries
        // RoutingTable aux= new RoutingTable ();
        // ...

        for(RouteEntry re : tab.get_routeset()){            
            if(tab.nextHop(re.dest)!= n.name){
                aux.add_route(re);
            }
        }
        
        // Return a vector with the aux routing table contents
        return (aux.size() > 0) ? aux.get_Entry_vector() : null;
    }

    /**
     * Send local ROUTE for all neighbours
     *
     * @return true if successful, false otherwise
     */
    public boolean send_local_ROUTE() {
        if ((tab == null) || !tab.is_valid()) {
            Log2("Cannot send ROUTE: invalid routing table\n");
            return false;
        }
        if (tab.size() > Router.MAX_ROUTINGTABLE_SIZE) {
            Log("Too many entries in routing table - ROUTE not sent\n");
            return false;
        }

        Log("Routing.send_local_ROUTE() not implemented\n");
        // COMPLETE IN STEP 3 
        // send the local vector to all the neighbor routers, one by one
        //    using the methods above (prepare_vec_for_neighbour and send_local_ROUTE_to_neighbour)
        
        // send local vector
         for (Neighbour pt : neig.values()) {
            if (pt.is_valid()) {
                send_local_ROUTE_to_neighbour(pt,prepare_vec_for_neighbour(pt));
            }
        }
        return true;
    }

    /**
     * Unmarshall a ROUTE packet and process it
     *
     * @param sender the sender address
     * @param dp datagram packet
     * @param ip IP address of the sender
     * @param dis input stream object
     * @return true if packet was handled successfully, false if error
     */
    public boolean process_ROUTE(char sender, DatagramPacket dp,
            String ip, DataInputStream dis) {
        //Log("Packet ROUTE not supported yet\n");
        if (sender == local_name) {
            // Packet loopback - ignored
            return true;
        }
        Entry[] vec;
        try {
            Log("PKT_ROUTE");
            String aux;
            aux = "(" + sender + ",";
            int TTL = dis.readInt();
            aux += "TTL=" + TTL + ",";
            int n = dis.readInt();
            aux += "EntryList(" + n + ": ";
            if ((n <= 0) || (n > Router.MAX_ROUTINGTABLE_SIZE)) {
                Log("\nInvalid Entry list length '" + n + "'\n");
                return false;
            }
            vec = new Entry[n];
            for (int i = 0; i < n; i++) {
                try {
                    vec[i] = new Entry(dis);
                } catch (IOException e) {
                    Log("\nERROR - Invalid vector Entry: " + e.getMessage() + "\n");
                    return false;
                }
                aux += (i == 0 ? "" : " ; ") + vec[i].toString();
            }
            Log(aux + ")\n");
            
            if (dis.available() > 0) {
                Log("\nERROR - Invalid ROUTE - extra bytes after end of message\n");
                return false;
            }


            Neighbour pt = neig.locate_neig(dp.getAddress().getHostAddress(), dp.getPort());
            if (pt == null) {
                Log("\nERROR - Invalid sender (" + dp.getAddress().getHostAddress() + " ; " + dp.getPort() + "), it is not a neighbor\n");
                return false;
            }
            if (pt.Name() != sender) {
                Log("\nERROR - Invalid sender name (" + sender + "), different from the neigbour table\n");
                return false;
            }
            
            // Update Router vector
            Log("Routing.process_ROUTE not implemented: ROUTE vector not stored\n");
            
            // COMPLETE in STEP 1 an STEP 6
            // STEP 1:
            //   Put here the code to store the vector received in the neighbour object associated
            pt.update_vec(vec, TTL);
            
            
            
            // STEP 6:
            //   Call 'network_changed' if the vector has changed!
            //   Use the code provided - there are functions that compare Entry array vectors ...
            //   if (win.is_sendIfChanges() /*"Send if change" is selected*/) { ... }
            
            //equal_Entry_vec();
            
            if(win.is_sendIfChanges()){
                if(Entry.equal_Entry_vec(vec,pt.vec)){
                    network_changed(true);
                }            
            }

            return true;
            
        } catch (IOException e) {
            Log("\nERROR - Packet too short\n");
            return false;
        } catch (Exception e) {
            Log("\nERROR - Invalid neighbour update\n");
            return false;
        }
    }

    /**
     * Test if a path is available through a Neighbour
     *
     * @param n Neighbour reference
     * @param dest destination address
     * @return true if available, false otherwise
     */
    private boolean is_path_available(Neighbour n, char dest) {
        if (!n.vec_valid()) {
            return false;
        }

        for (Entry Vec : n.Vec()) {
            if (Vec.dest == dest) {
                return Vec.dist < Router.MAX_DISTANCE;
            }
        }
        return false;
    }

    /**
     * Handle the end of the holddown of one RouteEntry destination
     *
     * @param re the RouteEntry that ended the hold down interval
     * @return true if the timer should be kept active, false otherwise
     */
    public synchronized boolean handle_holddown_timeout(RouteEntry re) {
        if (!tab.is_valid())
            return false;

        // COMPLETE IN STEP 7
        // Place here the code to handle the end of a hold down state from route re
        // Call network_changed if a new route is discovered
        
        tab.delete_routeEntry(re);
        network_changed(true);
        
        return true;
    }

    /**
     * Calculate the Routing table from area update
     *
     * @return true if the Routing table was modified, false otherwise
     */
    private synchronized boolean update_routing_table() {
        RoutingTable baktab = tab;
        tab = new RoutingTable();

        // Add local node
        tab.add_route(new RouteEntry(local_name, ' ', 0, win));

        Log("Routing.update_routing_table not implemented\n");
        // Implement here the distance vector algorithm:            
        // STEP 2:
        //      Implement the basic DV algorithm
        // Percorrer todos os vizinhos
        for(Neighbour n: neig.values()){
            for(Entry e: n.vec){
                if(n.is_valid() && n.vec_valid()){
                    Entry last_entry = tab.get_RouteEntry(e.dest);
                    if(last_entry.dist>e.dist || !(is_path_available(n,e.dest))){
                        RouteEntry Re = new RouteEntry(e.dest, tab.nextHop(e.dest),e.dist,win);
                        tab.add_route(Re);
                    }
                    
                }
                
            }
        }
        // Já existe? Se sim a distância nova é menor que a que já tenho?
        // Percorrer todas as entries(destinos) dos vizinhos e sacar a distância. Se for o melhor caminho (mais curto) atualizo a tabela.
        
        
        // STEP 7:
        //      Implement the hold down method, with the detection of hold down conditions
        //          and the modification to the RouteEntry, to count the hold down time
        //      This step is the toughest: You need to keep the RouteEntries in 
        //          holddown state while the new table is being created
        //      
        
        // Update the Routing table in the GUI 
        update_routing_window();
        return !tab.equal_RoutingTable(baktab);
    }

    /**
     * Display the Routing table in the GUI
     */
    public void update_routing_window() {
        Log2("update_routing_window\n");
        // update window
        Iterator<RouteEntry> rit = tab.iterator();
        RouteEntry r;
        for (int i = 0; i < tableObj.getRowCount(); i++) {
            if ((rit!=null) && rit.hasNext()) {
                r = rit.next();
                Log2("(" + r.dest + " : " + r.next_hop + " : " + r.dist + " : " + r.holddown_ending_time() + ")");
                tableObj.setValueAt("" + r.dest, i, 0);
                tableObj.setValueAt("" + r.next_hop, i, 1);
                if (r.is_holddown()) {
                    tableObj.setValueAt("HOLD", i, 2);
                    tableObj.setValueAt("" + r.holddown_ending_time(), i, 3);                
                } else {
                    tableObj.setValueAt("" + r.dist, i, 2);
                    tableObj.setValueAt("", i, 3);              
                }
            } else {
                tableObj.setValueAt("", i, 0);
                tableObj.setValueAt("", i, 1);
                tableObj.setValueAt("", i, 2);
                tableObj.setValueAt("", i, 3);
            }
        }
    }


    /* ------------------------------------ */
    // Announce timer

    /**
     * Run the timer responsible for sending periodic ROUTE packets to routers
     */
    private void start_announce_timer() {       
        
        // STEP 4:

        TimerTask task = new TimerTask() {
            public void run() {
                send_local_ROUTE();
                update_routing_table();
            }
        };
        Timer timer = new Timer();
        int delay = 0;
        timer.schedule(task, delay,period*1000);
    }

    /**
     * Stop the timer responsible for sending periodic distance packets to
     * neighbours
     */
    private void stop_announce_timer() {
        if (timer_announce != null) {
            timer_announce.stop();
            timer_announce = null;
        }
    }

    /**
     * Restart the timer responsible for sending periodic distance packets to
     * neighbours
     */
    private void reset_announce_timer() {
        if ((timer_announce != null) && timer_announce.isRunning()) {
            stop_announce_timer();
        }
        start_announce_timer();
    }

    
    /**
     * *************************************************************************
     * DATA HANDLING
     */
    /**
     * returns next hop to reach destination
     *
     * @param dest destination address
     * @return the address of the next hop, or ' ' if not found.
     */
    public char next_Hop(char dest) {
        RouteEntry nxt = tab.get_RouteEntry(dest);
        if (nxt == null) {
            return ' ';
        }
        return nxt.next_hop;
    }

    /**
     * send a DATA packet using the Routing table and the neighbor information
     *
     * @param dest destination address
     * @param dp datagram packet object
     */
    public void send_data_packet(char dest, DatagramPacket dp) {
        if (win.is_local_name(dest)) {
            // Send to local node
            try {
                dp.setAddress(InetAddress.getLocalHost());
                dp.setPort(ds.getLocalPort());
                ds.send(dp);
                win.DATA_snt++;
            } catch (UnknownHostException e) {
                Log("Error sending packet to himself: " + e + "\n");
            } catch (IOException e) {
                Log("Error sending packet to himself: " + e + "\n");
            }

        } else { // Send to Neighbour Router
            char prox = next_Hop(dest);
            if (prox == ' ') {
                Log("No route to destination: packet discarded\n");
            } else {
                // Lookup Neighbour
                Neighbour pt = neig.locate_neig(prox);
                if (pt == null) {
                    Log("Invalid neighbour (" + prox
                            + ") in routing table: packet discarder\n");
                    return;
                }
                try {
                    pt.send_packet(ds, dp);
                    win.DATA_snt++;
                } catch (IOException e) {
                    Log("Error sending DATA packet: " + e + "\n");
                }
            }
        }
    }

    /**
     * prepares a vec packet; adds local_name to path
     *
     * @param sender sender name
     * @param dest destination name
     * @param seq sequence number
     * @param msg message contents
     * @param path path already transverse
     * @return datagram packet to send
     */
    public DatagramPacket make_data_packet(char sender, char dest,
            int seq, String msg, String path) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(os);
        try {
            dos.writeByte(Router.PKT_DATA);
            dos.writeChar(sender);
            dos.writeChar(dest);
            dos.writeInt(seq);
            dos.writeShort(msg.length());
            dos.writeBytes(msg);
            dos.writeByte(path.length() + 1);
            dos.writeBytes(path + win.local_name());
        } catch (IOException e) {
            Log("Error encoding data packet: " + e + "\n");
            return null;
        }
        byte[] buffer = os.toByteArray();
        return new DatagramPacket(buffer, buffer.length);
    }

    /**
     * prepares a vec packet; adds local_name to path and send the packet
     *
     * @param sender sender name
     * @param dest destination name
     * @param seq sequence number
     * @param msg message contents
     * @param path path already transverse
     */
    public void send_data_packet(char sender, char dest, int seq, String msg,
            String path) {
        if (!Character.isUpperCase(sender)) {
            Log("Invalid sender '" + sender + "'\n");
            return;
        }
        if (!Character.isUpperCase(dest)) {
            Log("Invalid destination '" + dest + "'\n");
            return;
        }
        DatagramPacket dp = make_data_packet(sender, dest, seq, msg, path);
        if (dp != null) {
            send_data_packet(dest, dp);
        }
    }

    /**
     * unmarshals DATA packet e process it
     *
     * @param sender the sender of the packet
     * @param dp datagram packet received
     * @param ip IP of the sender
     * @param dis vec input stream
     * @return true if decoding was successful
     */
    public boolean process_DATA(char sender, DatagramPacket dp,
            String ip, DataInputStream dis) {
        try {
            Log("PKT_DATA");
            if (!Character.isUpperCase(sender)) {
                Log("Invalid sender '" + sender + "'\n");
                return false;
            }
            // Read Dest
            char dest = dis.readChar();
            // Read seq
            int seq = dis.readInt();
            // Read message
            int len_msg = dis.readShort();
            if (len_msg > 255) {
                Log(": message too long (" + len_msg + ">255)\n");
                return false;
            }
            byte[] sbuf1 = new byte[len_msg];
            int n = dis.read(sbuf1, 0, len_msg);
            if (n != len_msg) {
                Log(": Invalid message length\n");
                return false;
            }
            String msg = new String(sbuf1, 0, n);
            // Read path
            int len_path = dis.readByte();
            if (len_path > Router.MAX_PATH_LEN) {
                Log(": path length too long (" + len_path + ">" + Router.MAX_PATH_LEN
                        + ")\n");
                return false;
            }
            byte[] sbuf2 = new byte[len_path];
            n = dis.read(sbuf2, 0, len_path);
            if (n != len_path) {
                Log(": Invalid path length\n");
                return false;
            }
            String path = new String(sbuf2, 0, n);
            Log(" (" + sender + "-" + dest + "," + seq + "):'" + msg + "':Path='" + path + win.local_name() + "'\n");
            // Test Routing table
            if (win.is_local_name(dest)) {
                // Arrived at destination
                Log("DATA packet reached destination\n");
                return true;
            } else {
                char prox = next_Hop(dest);
                if (prox == ' ') {
                    Log("No route to destination: packet discarded\n");
                    return false;
                } else {
                    // Send packet to next hop
                    send_data_packet(sender, dest, seq, msg, path);
                    return true;
                }
            }
        } catch (IOException e) {
            Log(" Error decoding data packet: " + e + "\n");
        }
        return false;
    }

    /**
     * *************************************************************************
     * Log functions
     */
    /**
     * Output the string to the log text window and command line
     *
     * @param s log string
     */
    public final void Log(String s) {
        win.Log(s);
    }

    /**
     * Auxiliary log function - when more detail is required remove the comments
     *
     * @param s log string
     */
    public final void Log2(String s) {
        win.Log2(s);
    }
}
