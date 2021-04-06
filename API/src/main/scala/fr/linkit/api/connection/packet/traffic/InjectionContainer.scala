package fr.linkit.api.connection.packet.traffic
import fr.linkit.api.connection.packet.{Bundle, DedicatedPacketCoordinates, Packet, PacketAttributes}

trait InjectionContainer {

    def makeInjection(packet: Packet, attributes: PacketAttributes, coordinates: DedicatedPacketCoordinates): PacketInjection

    def makeInjection(bundle: Bundle): PacketInjection

    def isInjecting(injection: PacketInjection): Boolean

}
