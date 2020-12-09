package fr.overridescala.linkkit.api.packet.channel

import java.util.concurrent.{BlockingDeque, LinkedBlockingDeque}

import fr.overridescala.linkkit.api.exceptions.UnexpectedPacketException
import fr.overridescala.linkkit.api.packet.fundamental.DataPacket
import fr.overridescala.linkkit.api.packet.{Packet, PacketCoordinates, TrafficHandler}
import fr.overridescala.linkkit.api.system.Reason


//TODO doc
class SyncPacketChannel(override val connectedID: String,
                        override val identifier: Int,
                        packetCacheSize: Int,
                        traffic: TrafficHandler) extends PacketChannel.Sync(traffic) {


    /**
     * this blocking queue stores the received packets until they are requested
     * */
    private val queue: BlockingDeque[Packet] = new LinkedBlockingDeque(packetCacheSize)

    /**
     * add a packet into the PacketChannel. the PacketChannel will stop waiting in [[PacketChannel#nextPacket]] if it where waiting for a packet
     *
     * @param packet the packet to add
     * @throws UnexpectedPacketException if the packet id not equals the channel task ID
     * */
    override def injectPacket(packet: Packet, coordinates: PacketCoordinates): Unit = {
        queue.addFirst(packet)
    }

    override def close(reason: Reason): Unit = {
        super.close(reason)
        queue.clear()
    }

    /**
     * Waits until a data packet is received and concerned about this task.
     *
     * @return the received packet
     * @see [[DataPacket]]
     * */
    override def nextPacket(): Packet = {
        val packet = queue.takeLast()
        //handler.notifyPacketUsed(packet, coordinates)
        packet
    }


    override def nextPacketAsP[P <: Packet](): P =
        nextPacket().asInstanceOf[P]

    /**
     * @return true if this channel contains stored packets. In other words, return true if [[nextPacket]] will not wait
     * */
    override def haveMorePackets: Boolean =
        !queue.isEmpty
}
