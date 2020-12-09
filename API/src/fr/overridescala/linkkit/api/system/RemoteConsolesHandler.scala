package fr.overridescala.linkkit.api.system

import java.util.concurrent.ThreadLocalRandom

import fr.overridescala.linkkit.api.Relay
import fr.overridescala.linkkit.api.packet.fundamental.DataPacket
import fr.overridescala.linkkit.api.system.RemoteConsolesHandler.ConsolesCollectorID

import scala.collection.mutable

class RemoteConsolesHandler(relay: Relay) {

    private val outConsoles = mutable.LinkedHashMap.empty[String, RemoteConsole]
    private val errConsoles = mutable.LinkedHashMap.empty[String, RemoteConsole.Err]

    private val consoleCollector = relay.createAsyncCollector(ConsolesCollectorID)

    if (relay.configuration.enableRemoteConsoles)
        consoleCollector.onPacketReceived((packet, coos) => {
            val data = packet.asInstanceOf[DataPacket]
            val owner = coos.senderID
            val consoleType = data.header
            val consoleChannelId = new String(data.content).toInt
            val channel = relay.createAsyncChannel(coos.senderID, consoleChannelId)

            consoleType match {
                case "out" => outConsoles.put(owner, RemoteConsole.out(channel))
                case "err" => errConsoles.put(owner, RemoteConsole.err(channel))
            }
        })

    def getOut(targetId: String): RemoteConsole = {
        if (!relay.configuration.enableRemoteConsoles)
            return RemoteConsole.mock()

        if (outConsoles.contains(targetId))
            return outConsoles(targetId)

        val consoleChannelId = ThreadLocalRandom.current().nextInt()
        consoleCollector.sendPacket(DataPacket("out", consoleChannelId.toString), targetId)

        val channel = relay.createAsyncChannel(targetId, consoleChannelId)
        val remoteConsole = RemoteConsole.out(channel)
        outConsoles.put(targetId, remoteConsole)
        remoteConsole
    }

    def getErr(targetId: String): RemoteConsole.Err = {
        if (!relay.configuration.enableRemoteConsoles)
            return RemoteConsole.mockErr()

        if (errConsoles.contains(targetId))
            return errConsoles(targetId)

        val consoleChannelId = ThreadLocalRandom.current().nextInt()
        consoleCollector.sendPacket(DataPacket("err", consoleChannelId.toString), targetId)

        val channel = relay.createAsyncChannel(targetId, consoleChannelId)
        val remoteConsole = RemoteConsole.err(channel)
        errConsoles.put(targetId, remoteConsole)
        remoteConsole
    }

    def linkErr(targetID: String, identifier: Int): Unit = {
        val channel = relay.createAsyncChannel(targetID, identifier)
        val remoteConsole = RemoteConsole.err(channel)
        errConsoles.put(targetID, remoteConsole)
    }

    def linkOut(targetId: String, identifier: Int): Unit = {
        val channel = relay.createAsyncChannel(targetId, identifier)
        val remoteConsole = RemoteConsole.out(channel)
        outConsoles.put(targetId, remoteConsole)
    }


}

object RemoteConsolesHandler {
    val ConsolesCollectorID = 7
}
