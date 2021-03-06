package fr.`override`.linkit.client

import com.sun.glass.ui.Application
import fr.`override`.linkit.api.packet.fundamental.RefPacket.ArrayValPacket
import fr.`override`.linkit.api.packet.serialization.RawObjectSerializer
import fr.`override`.linkit.api.packet.{DedicatedPacketCoordinates, Packet, PacketCoordinates}
import javafx.geometry.Rectangle2D
import javafx.scene.image.{PixelFormat, WritableImage}
import javafx.scene.robot.Robot

import java.util.concurrent.ThreadLocalRandom
import scala.annotation.tailrec
import scala.util.control.NonFatal

object OtherTests {

    private val serializer = RawObjectSerializer
    private val randomizer = ThreadLocalRandom.current()

    def main(args: Array[String]): Unit = try {
        Application.run(() => {
            makeSomething(1)
        })
    } catch {
        case NonFatal(e) => e.printStackTrace()
    }


    @tailrec
    def makeSomething(times: Int): Unit = {

        val region = new Rectangle2D(0, 0, 1920, 1080)
        val robot = new Robot()
        val writable = new WritableImage(1920, 1080)
        val reader = writable.getPixelReader
        val buffer = new Array[Int](1920 * 1080)

        robot.getScreenCapture(writable, region)
        println("Capture created !")
        reader.getPixels(0, 0, 1920, 1080, PixelFormat.getIntArgbInstance, buffer, 0, region.getWidth.toInt)

        val packet = ArrayValPacket(buffer)
        val coords = DedicatedPacketCoordinates(11, "a", "a")

        println(s"SERIALIZING... ($packet & $coords)")
        val t0 = System.currentTimeMillis()
        val bytes = serialize(packet, coords)
        val t1 = System.currentTimeMillis()
        println()
        println()
        println()
        println(s"bytes = ${new String(bytes).replace('\r', ' ')} (length: ${bytes.length}) took ${t1 - t0}ms")
        println()
        println()
        println()

        //val sBytes = Utils.serialize(Array(packet, coords))
        //val standardSerial = new String(sBytes).replace('\r', ' ')
        //println("Standard method : " + standardSerial + s" (length: ${sBytes.length})")
        //println("GSON method : " + new Gson().toJson(Array(coords, packet)) + s" (length: ${sBytes.length})")

        println("DESERIALIZING...")
        println()
        println()
        val (coords0, packet0) = deserialize(bytes)

        println(s"coords0 = ${coords0}")
        println(s"packet0 = ${packet0}")
        println(s"refCoords = ${coords}")
        println(s"refPacket = ${packet}")

        if (times > 1)
            makeSomething(times - 1)

    }

    protected def serializeInt(value: Int): Array[Byte] = {
        Array[Byte](
            ((value >> 24) & 0xff).toByte,
            ((value >> 16) & 0xff).toByte,
            ((value >> 8) & 0xff).toByte,
            ((value >> 0) & 0xff).toByte
        )
    }

    private def serialize(packet: Packet, coordinates: PacketCoordinates): Array[Byte] = {
        serializer.serialize(Array(coordinates, packet))
    }

    private def deserialize(bytes: Array[Byte]): (PacketCoordinates, Packet) = {
        val array = serializer.deserializeAll(bytes)
        (array(0).asInstanceOf[PacketCoordinates], array(1).asInstanceOf[Packet])
    }


    case class StreamPacket(buffer: Array[Int]) extends Packet

}
