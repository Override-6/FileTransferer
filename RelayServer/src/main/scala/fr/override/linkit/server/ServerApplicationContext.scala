package fr.`override`.linkit.server

import fr.`override`.linkit.api.connection.ConnectionContext
import fr.`override`.linkit.api.connection.network.Network
import fr.`override`.linkit.api.connection.packet.serialization.PacketTranslator
import fr.`override`.linkit.api.connection.packet.traffic.{PacketInjectableContainer, PacketTraffic}
import fr.`override`.linkit.api.local.ApplicationContext
import fr.`override`.linkit.api.local.plugin.PluginManager
import fr.`override`.linkit.api.local.system.event.EventNotifier
import fr.`override`.linkit.core.connection.packet.serialization.CompactedPacketTranslator
import fr.`override`.linkit.core.local.concurrency.BusyWorkerPool
import fr.`override`.linkit.core.local.plugin.LinkitPluginManager
import fr.`override`.linkit.core.local.system.event.SimpleEventNotifier
import fr.`override`.linkit.server.config.ServerApplicationConfiguration
import fr.`override`.linkit.server.network.ServerNetwork

class ServerApplicationContext(override val configuration: ServerApplicationConfiguration) extends ApplicationContext with PacketInjectableContainer {

    private val workerPool = new BusyWorkerPool(configuration.nWorkerThreadFunction(0))

    val identifier              : String            = configuration.identifier
    val eventNotifier           : EventNotifier     = new SimpleEventNotifier
    val traffic                 : PacketTraffic     = new ServerPacketTraffic(this)
    val translator              : PacketTranslator  = new CompactedPacketTranslator(identifier, configuration.hasher)
    val network                 : Network           = new ServerNetwork(this, traffic)
    override val pluginManager  : PluginManager     = new LinkitPluginManager(configuration.fsAdapter)


    override def getConnection(identifier: String): ConnectionContext = ???

    override def runLater(task: => Unit): Unit = workerPool.runLater(task)
}
