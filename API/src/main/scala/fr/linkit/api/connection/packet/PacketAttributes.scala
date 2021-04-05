package fr.linkit.api.connection.packet

trait PacketAttributes extends Serializable {

    def getAttribute[S <: Serializable](key: Serializable): Option[S]

    def getPresence[S <: Serializable](presence: PacketAttributesPresence): Option[S]

    def putAttribute(key: Serializable, value: Serializable): this.type

    def putPresence(presence: PacketAttributesPresence, value: Serializable): this.type

    def drainAttributes(other: PacketAttributes): this.type

    def isEmpty: Boolean

    def nonEmpty: Boolean = !isEmpty

}