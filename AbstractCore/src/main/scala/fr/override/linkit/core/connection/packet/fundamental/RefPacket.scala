package fr.`override`.linkit.core.connection.packet.fundamental

import fr.`override`.linkit.api.connection.packet.Packet

import java.io.Serializable
/**
 * This trait is used to transport a packet for specific ref serializable types
 * such as strings or arrays.
 * */
sealed trait RefPacket[A <: Serializable] extends Packet {
    /**
     * The main value of the packet
     * */
    val value: A
    def casted[C <: A]: C = value.asInstanceOf[C]
}

object RefPacket {
    /**
     * Represents a packet that contains a string value
     * */
    case class StringPacket(override val value: String) extends RefPacket[String]

    /**
     * Represents a packet that contains a serializable value of a specified type
     * */
    case class AnyRefPacket[A <: Serializable] private(override val value: A) extends RefPacket[A]

    /**
     * Represents a packet that contains a serializable value
     * */
    case class ObjectPacket(override val value: Serializable) extends RefPacket[Serializable]

    /**
     * Represents a packet that contains an array of serialized values of a specified type.
     * */
    //TODO Fix Array[Serializable] and Array[Any] cast exception
    case class ArrayRefPacket[A <: Any](override val value: Array[A]) extends RefPacket[Array[A]] {

        def apply(i: Int): Any = value(i)

        def isEmpty: Boolean = value.isEmpty

        def contains(a: Any): Boolean = {
            a match {
                case s: A => value.contains(s)
                case _ => false
            }
        }

        def length: Int = value.length

        override def toString: String = s"ArrayRefPacket(${if (value == null) "null" else value.mkString(",")})"
    }

    /**
     * Represents a packet that contains an array of serializable values
     * */
    //TODO Fix Array[Serializable] and Array[Any] cast exception
    class ArrayObjectPacket(array: Array[Any]) extends ArrayRefPacket[Any](array)
    object ArrayObjectPacket {
        def apply(array: Array[Any]): ArrayObjectPacket = new ArrayObjectPacket(array)
    }

    /**
     * Represents a packet that contains an array of primitive values
     * */
    case class ArrayValPacket[A <: AnyVal](override val value: Array[A]) extends RefPacket[Array[A]] {
        def apply(i: Int): A = value(i)

        def isEmpty: Boolean = value.isEmpty

        def contains(a: A): Boolean = value.contains(a)

        def length: Int = value.length
        Thread.currentThread().getContextClassLoader
        override def toString: String = s"ArrayValPacket(${value.mkString(",")})"
    }

    /**
     * Alias for [[AnyRefPacket.apply()]]
     * */
    def apply[A <: Serializable](value: A): AnyRefPacket[A] = AnyRefPacket(value)

    /**
     * Implicit unboxing of a RefPacket's value.
     * */
    implicit def unbox[A <: Serializable](packet: RefPacket[A]): A = packet.value

}
