/**
 * Redes Integradas de Telecomunicacoes
 * MIEEC/MEEC 2022/2023
 *
 * RouteEntry.java
 *
 * Hold routing table entries
 *
 * Updated on August 26, 2022
 * @author  Luis Bernardo && ????? && ?????
 */
package router;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


public class RouteEntry extends Entry {

// Fields inherited from Entry
//    public char dest;
//    public int dist;
    
// New fields
    /** next hop */
    public char next_hop;
    /** Holddown timer */
    private javax.swing.Timer holddown_timer;
    /** Holdown start time */
    private Date holddown_stime;
    /** Holdown duration */
    private long holddown_duration;
    /** Routing object */
    private Routing router;
    /** RouteEntry selfpointer */
    public RouteEntry self;
    /** Log log */
    private final Log log;

    /**
     * Constructor - create an empty instance to a destination
     * @param dest destination address
     * @param log  Log object 
     */
    public RouteEntry(char dest, Log log) {
        super(dest, Router.MAX_DISTANCE);
        next_hop= ' ';
        holddown_timer= null;
        holddown_stime= null;
        holddown_duration= 0;
        router= null;
        this.log= log;
        self= this;
        }

    /**
     * Constructor - clone an existing entry
     * @param src  object that will be cloned
     */
    public RouteEntry(RouteEntry src) {
        super(src);
        next_hop= src.next_hop;
        holddown_timer= src.holddown_timer;
        holddown_stime= src.holddown_stime;
        holddown_duration= src.holddown_duration;
        router= src.router;
        log= src.log;
        self= this;
    }

    /**
     * Constructor - create an entry defining all fields
     * @param dest      destination address
     * @param next_hop  next hop address
     * @param dist      distance to next hop
     * @param log       Log object
     */
    public RouteEntry(char dest, char next_hop, int dist, Log log) {
        super(dest, dist);
        this.next_hop= next_hop;
        this.holddown_stime= null;
        this.holddown_duration= 0;
        this.holddown_timer= null;
        router= null;
        this.log= log;
        self= this;
    }
    
// Holdown algorithm specific field
    
    /**
     * Set holddown value
     * @param duration  Duration of the hold down time
     * @param router    router object
     */
    public void start_holddown(int duration, Routing router) { 
        start_holddown_timer(duration); 
        this.holddown_stime= new Date();
        this.holddown_duration= duration; 
        this.router= router;
    }
    
    /**
     * Decrement the holdown value
     * @param update_table  if true, inform router process about hold down timeout
     */
    public void stop_holddown(boolean update_table) { 
        stop_holddown_timer();
        this.holddown_stime= null;
        this.holddown_duration= 0;
        if (update_table) {
            // Update the routing table
            router.handle_holddown_timeout(this);
        }
        this.router= null;
    }
    
    /**
     * Test if destination is in hold down
     * @return true if destination is hold down, false otherwise
     */
    public boolean is_holddown() { 
        return (holddown_timer != null); 
    }
    
    
    /**
     * Return time until the end of hold down state
     * @return number of miliseconds
     */
    public long holddown_ending_time() {
        if (!is_holddown() || (holddown_stime==null) || (holddown_duration<=0))
            return 0;
        return (holddown_stime.getTime()+holddown_duration-System.currentTimeMillis()); 
    }
    
    
    /* ------------------------------------ */
    // Holddown timer
    /**
     * Start and run the timer responsible for counting the hold down time
     */
    private void start_holddown_timer(int duration) {
     
        //  STEP 7
        //  Place here the code to create and start the holddown timer
        //  when the timer ends, it must call stop_holddown 

        TimerTask decrementar = new TimerTask() {
            int contador=duration;
            public void run() {
                contador--;
                if(contador==0){
                    stop_holddown(true);
                }
            }
        };
        Timer timer = new Timer();
        int delay = 0;
        timer.schedule(decrementar,delay,1000);        
    }

    /**
     * Stop the timer responsible for counting the hold down time
     */
    private void stop_holddown_timer() {
    
        // STEP 7
        // Place here the code to stop the holddown_timer
        
          
    }
    
}
