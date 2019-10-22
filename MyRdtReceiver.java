import sim.Packet;
import sim.RdtReceiver;

import static sim.Packet.RDT_PKTSIZE;

/**
 * My reliable data transfer receiver
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
 *     Routines that you can call at the receiver:
 *     {@link #getSimulationTime()}         get simulation time (in seconds)
 *     {@link #sendToLowerLayer(Packet)}    pass a packet to the lower layer at the receiver
 *     {@link #sendToUpperLayer(byte[])}    deliver a message to the upper layer at the receiver
 * </pre>
 *
 * @author Jiupeng Zhang
 * @author Kai Shen
 * @since 10/04/2019
 */
public class MyRdtReceiver extends RdtReceiver {

    byte acc_ack;
    /**
     * Receiver initialization
     */
    public MyRdtReceiver() {
        acc_ack = -1;
    }

    /**
     * Event handler, called when a packet is passed from the lower
     * layer at the receiver
     */
    public void receiveFromLowerLayer(Packet packet) {
        // todo: write code here...

        // 1-byte header indicating the size of the payload
        int header_size = 3;

        // sanity check in case the packet is corrupted
        int size = packet.data[0] & 0xFF;
        if (size > RDT_PKTSIZE - header_size) size = RDT_PKTSIZE - header_size;

        // check whether the packet is valid
        if(!validatePacletFromLowerLayer(packet)) return ;

        // construct a message and deliver it to the upper layer
        byte[] message = new byte[size];
        System.arraycopy(packet.data, header_size, message, 0, size);
        sendToUpperLayer(message);
    }

    public boolean validatePacletFromLowerLayer(Packet packet){
        Packet ack_packet = new Packet();

        // check whether there is a bit flipped
        //if(validateCheckSum(packet.data) == false) return false;
        System.out.printf("Receiver: acc_ack = %d, received seq = %d\n", acc_ack, packet.data[1]);
        byte seq = packet.data[1];
        byte expected_seq = acc_ack == (byte) 0x7F ? (byte) 0x00 : (byte) (acc_ack + (byte) 1);
        if( seq - expected_seq > 0x4F || (expected_seq - seq < 10 && expected_seq - seq > 0)){
            System.out.printf("Receiver: drop the packet %d\n", seq);
            return false;
        }
        if(expected_seq == seq) acc_ack = expected_seq;

        ack_packet.data[0] = (byte) 0;
        ack_packet.data[1] = expected_seq == seq ? expected_seq : acc_ack;
        ack_packet.data[2] = checkSum(ack_packet);
        sendToLowerLayer(ack_packet);
        if(seq != expected_seq){
            System.out.printf("Receiver: received packet %d but need packet %d\n", seq,expected_seq);
        }
        else {
            System.out.printf("Receiver: accepted packet %d\n", seq);
        }
        return seq == acc_ack;
    }

    public boolean validateCheckSum(byte [] data){
        byte sum = 0;
        for(int i = 0 ; i < data.length ; i++){
            if(i == 2) continue;
            if(sum > 0xFF - data[i]) sum += data[i] + 1;
        }
        return sum + data[2] == 0xFF;
    }

    public byte checkSum(Packet packet){
        return (byte) 0;
    }
}
