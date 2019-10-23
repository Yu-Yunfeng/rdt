import sim.Packet;
import sim.RdtSender;

import static sim.Packet.RDT_PKTSIZE;

import java.util.*;

/**
 * My reliable data transfer sender
 * <pre>
 *     This implementation assumes there is no packet loss, corruption,
 *     or reordering. You will need to enhance it to deal with all these
 *     situations. In this implementation, the packet format is laid out
 *     as the following:
 *
 *     |<-  1 byte  ->|<-             the rest            ->|
 *     | payload size |<-             payload             ->|
 *
 *     The first byte of each packet indicates the size of the payload
 *     (excluding this single-byte header)
 *
 *     Routines that you can call at the sender:
 *     {@link #getSimulationTime()}         get simulation time (in seconds)
 *     {@link #startTimer(double)}          set a specified timeout (in seconds)
 *     {@link #stopTimer()}                 stop the sender timer
 *     {@link #isTimerSet()}                check whether the sender timer is being set
 *     {@link #sendToLowerLayer(Packet)}    pass a packet to the lower layer at the sender
 * </pre>
 *
 * @author Jiupeng Zhang
 * @author Kai Shen
 * @since 10/04/2019
 */
public class MyRdtSender extends RdtSender {

    int window_size;
    int seq;
    List<Packet> packets;
    Queue<Packet> wait_packets;
    byte lastack;
    byte lastack_n;
    double timeout = 0.02;
    /**
     * Sender initialization
     */
    public MyRdtSender() {
        window_size = 10;
        seq = 0;
        packets = new ArrayList<>();
        wait_packets = new LinkedList<>();
        lastack = -1;
        lastack_n = 0;
    }

    /**
     * Event handler, called when a message is passed from the upper
     * layer at the sender
     */
    public void receiveFromUpperLayer(byte[] message) {
        // todo: write code here...

        // 1-byte header indicating the size of the payload
        int header_size = 3;

        // maximum payload size
        int maxpayload_size = RDT_PKTSIZE - header_size;

        // split the message if it is too big

        // the cursor always points to the first unsent byte in the message
        int cursor = 0;
        System.out.printf("Message has %d packets and %d bytes\n", (message.length+maxpayload_size-1) / maxpayload_size, message.length);
        while (message.length - cursor > maxpayload_size) {
            // fill in the packet
            Packet pkt = new Packet();
            pkt.data[0] = (byte) maxpayload_size;
            System.out.printf("make seq = %d\n", seq);
            pkt.data[1] = (byte) (seq);
            pkt.data[2] = CheckSum(pkt);
            System.arraycopy(message, cursor, pkt.data, header_size, maxpayload_size);

            // send it out through the lower layer
            if(packets.size() == window_size){
                //System.out.printf("The window now is full, %d, %d, %d\n", array_begin, array_end, (array_end - array_begin));
                wait_packets.add(pkt);
            }
            else {
                if(!isTimerSet()) startTimer(timeout);
                System.out.printf("Sender begins to send packet %d\n", pkt.data[1]);
                packets.add(pkt);
                System.out.printf("packet %d pushed into packets\n", pkt.data[1]);
                Packet tmp_pkt = copyPacket(pkt);
                sendToLowerLayer(tmp_pkt);
            }


            seq = seq == 0x7F ? 0 : seq + 1;

            // move the cursor
            cursor += maxpayload_size;
        }

        // send out the last packet
        if (message.length > cursor) {
            // fill in the packet
            Packet pkt = new Packet();
            System.out.printf("make seq = %d\n", seq);
            pkt.data[0] = (byte) (message.length - cursor);
            pkt.data[1] = (byte) (seq);
            pkt.data[2] = CheckSum(pkt);
            System.arraycopy(message, cursor, pkt.data, header_size, pkt.data[0]);

            // send it out through the lower layer
            if (packets.size() == window_size){
                //System.out.printf("The window now is full, %d, %d, %d\n", );
                wait_packets.add(pkt);
            }
            else{
                if(!isTimerSet()) startTimer(timeout);
                System.out.printf("packet %d pushed into packets\n", pkt.data[1]);
                packets.add(pkt);
                System.out.printf("Sender begins to send packet %d\n", pkt.data[1]);
                Packet tmp_pkt = copyPacket(pkt);
                sendToLowerLayer(tmp_pkt);
            }
            seq = seq == 0x7F ? 0 : seq + 1;
        }
    }

    /**
     * Event handler, called when a packet is passed from the lower
     * layer at the sender
     */
    public void receiveFromLowerLayer(Packet packet) {
        // todo: write code here...

        if(packets.size() > 0) System.out.printf("Sender: received ack: %d and the first packet in window is %d\n", packet.data[1], packets.get(0).data[1]);
        int ack = packet.data[1];
        if(packets.size() == 0) return;
        int firstpkt = (int) packets.get(0).data[1];
        if(firstpkt < window_size && firstpkt + 128 - ack != 1 && firstpkt + 128 - ack < 10) return;
        if(firstpkt >= window_size && firstpkt - ack != 1 && firstpkt - ack < window_size && firstpkt > ack) return;
//        System.out.printf("The array_end = %d and the original array_begin = %d ",array_end ,array_begin);
        if((ack + 1) % 128 == firstpkt) {
            System.out.printf("ack = %d, first packet = %d\n", ack, firstpkt);
            if(lastack == ack) lastack_n += 1;
            else{
                lastack = (byte) ack;
                lastack_n = 1;
            }

            if(lastack_n >= 3){
                lastack = -1;
                lastack_n = 0;
                System.out.printf("Sender: received 3 same ack\n");
                onTimeout();
                stopTimer();
                startTimer(timeout);
            }

            return;
        }
        if(ack >= packets.get(0).data[1] && ack - packets.get(0).data[1] < window_size) {
            //System.out.printf("begin = %d, end = %d, waitpackets = %d\n", array_begin, array_end, wait_packets.size());
            while(!packets.isEmpty() && packets.get(0).data[1] <= ack && ack - packets.get(0).data[1] < window_size) {
                System.out.printf("remove packet %d, ", packets.get(0).data[1]);
                packets.remove(0);
            }
            System.out.printf("\n");
            for(int i = 0 ; i < packets.size() ; i++) System.out.printf("%d ", packets.get(i).data[1]);

            System.out.printf("\n");
            while(!wait_packets.isEmpty() && packets.size() < window_size) {
                Packet pkt = wait_packets.poll();
                packets.add(pkt);
                System.out.printf("packet %d push into packets\n", pkt.data[1]);
                Packet tmp_pkt = copyPacket(pkt);
                sendToLowerLayer(tmp_pkt);
            }
            //System.out.printf("and now the array_begin = %d \n", array_begin);
        }
        else if(ack + 128 - firstpkt  <= window_size) {
            while(!packets.isEmpty() && packets.get(0).data[1] != ack) {
                System.out.printf("remove packet %d, ", packets.get(0).data[1]);
                packets.remove(0);
            }
            if(!packets.isEmpty()) {
                System.out.printf("remove packet %d, ", packets.get(0).data[1]);
                packets.remove(0);
            }
            while(!wait_packets.isEmpty() && packets.size() < window_size) {
                Packet pkt = wait_packets.poll();
                packets.add(pkt);
                Packet tmp_pkt = copyPacket(pkt);
                sendToLowerLayer(tmp_pkt);
            }
            //System.out.printf("and now the array_begin = %d \n", array_begin);
        }
        startTimer(timeout);

//        else onTimeout();
    }

    /**
     * Event handler, called when the timer expires
     */
    public void onTimeout() {
        // todo: write code here...
//        System.out.printf("Sender: Time out, resend\n");
//        sendToLowerLayer(packets.get(0));
        for (int i = 0 ; i < packets.size() ; i++){
            Packet tmp_pkt = copyPacket(packets.get(i));
            sendToLowerLayer(tmp_pkt);
        }
    }

    public byte CheckSum(Packet packet){
        return (byte) 0;
    }

    public Packet copyPacket(Packet packet){
        Packet pkt = new Packet();
        System.arraycopy(packet.data, 0, pkt.data, 0, packet.data.length);
        return pkt;
    }
}
