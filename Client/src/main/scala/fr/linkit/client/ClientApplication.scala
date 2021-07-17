/*
 *  Copyright (c) 2021. Linkit and or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can only use it for personal uses, studies or documentation.
 *  You can download this source code, and modify it ONLY FOR PERSONAL USE and you
 *  ARE NOT ALLOWED to distribute your MODIFIED VERSION.
 *
 *  Please contact maximebatista18@gmail.com if you need additional information or have any
 *  questions.
 */

package fr.linkit.client

import fr.linkit.api.connection.{ConnectionContext, ConnectionInitialisationException, ExternalConnection}
import fr.linkit.api.local.concurrency.workerExecution
import fr.linkit.api.local.resource.external.ResourceFolder
import fr.linkit.api.local.system
import fr.linkit.api.local.system._
import fr.linkit.api.local.system.config.ApplicationInstantiationException
import fr.linkit.client.ClientApplication.Version
import fr.linkit.client.local.config.{ClientApplicationConfiguration, ClientConnectionConfiguration}
import fr.linkit.client.connection.{ClientConnection, ClientDynamicSocket}
import fr.linkit.engine.local.LinkitApplication
import fr.linkit.engine.local.concurrency.pool.BusyWorkerPool
import fr.linkit.engine.local.system.{EngineConstants, Rules, StaticVersions}

import scala.collection.mutable
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class ClientApplication private(override val configuration: ClientApplicationConfiguration, resources: ResourceFolder) extends LinkitApplication(configuration, resources) {

    override protected val appPool             = new BusyWorkerPool(configuration.nWorkerThreadFunction(0), "Application")
    private            val connectionCache     = mutable.HashMap.empty[Any, ExternalConnection]
    @volatile private var connectionCount: Int = 0

    override def countConnections: Int = connectionCount

    override val versions: Versions = StaticVersions(ApiConstants.Version, EngineConstants.Version, Version)

    @workerExecution
    override def shutdown(): Unit = {
        appPool.ensureCurrentThreadOwned("Shutdown must be performed into Application's pool")
        ensureAlive()
        AppLogger.info("Client application is shutting down...")

        listConnections.foreach(connection => {
            try {
                //Connections will unregister themself from connectionCache automatically
                connection.shutdown()
                connectionCount -= 1
            } catch {
                case NonFatal(e) => AppLogger.printStackTrace(e)
            }
        })
    }

    override def listConnections: Iterable[ConnectionContext] = connectionCache.values.toSet

    override def getConnection(identifier: String): Option[ExternalConnection] = {
        connectionCache.get(identifier).orElse(connectionCache.find(_._2.boundIdentifier == identifier).map(_._2))
    }

    override def getConnection(port: Int): Option[ExternalConnection] = {
        connectionCache.values.find(_.port == port)
    }

    @throws[ConnectionInitialisationException]("If something went wrong during the connection's opening")
    @workerExecution
    def openConnection(config: ClientConnectionConfiguration): ExternalConnection = {
        appPool.ensureCurrentThreadOwned("Connection creation must be executed by the client application's thread pool")

        val identifier = config.identifier
        if (!Rules.IdentifierPattern.matcher(identifier).matches())
            throw new ConnectionInitialisationException("Provided identifier does not matches Client's rules.")

        connectionCount += 1
        appPool.setThreadCount(configuration.nWorkerThreadFunction(connectionCount)) //expand the pool for the new connection that will be opened

        AppLogger.info(s"Creating connection to address '${config.remoteAddress}'...")
        val address       = config.remoteAddress
        val dynamicSocket = new ClientDynamicSocket(address, config.socketFactory)
        dynamicSocket.reconnectionPeriod = config.reconnectionMillis
        dynamicSocket.connect("UnknownServerIdentifier")
        AppLogger.trace("Socket accepted !")

        val connection = try {
            ClientConnection.open(dynamicSocket, this, config)
        } catch {
            case NonFatal(e) =>
                connectionCount -= 1
                throw new ConnectionInitialisationException(s"Could not open connection with server $address : ${e.getMessage}", e)
        }

        val serverIdentifier: String = connection.boundIdentifier
        val port                     = config.remoteAddress.getPort

        connectionCache.put(serverIdentifier, connection)
        connectionCache.put(port, connection)
        AppLogger.info(s"Connection Sucessfully bound to $address ($serverIdentifier)")
        connection
    }

    @throws[NoSuchElementException]("If no connection is found into the application's cache.")
    def unregister(connectionContext: ExternalConnection): Unit = {
        import connectionContext.{boundIdentifier, currentIdentifier}

        connectionCache.remove(currentIdentifier)
        connectionCount -= 1
        val newThreadCount = configuration.nWorkerThreadFunction(connectionCount)
        appPool.setThreadCount(newThreadCount)

        AppLogger.info(s"Connection '$currentIdentifier' bound to $boundIdentifier was detached from application.")
    }

}

object ClientApplication {

    val Version: Version = system.Version("Client", "1.0.0", false)

    @volatile private var initialized = false

    def launch(config: ClientApplicationConfiguration, otherSources: Class[_]*): ClientApplication = {
        if (initialized)
            throw new IllegalStateException("Client Application is already launched.")

        val resources = LinkitApplication.prepareApplication(Version, config, otherSources)

        val clientApp = try {
            AppLogger.info("Instantiating Client application...")
            new ClientApplication(config, resources)
        } catch {
            case NonFatal(e) =>
                throw new ApplicationInstantiationException("Could not instantiate Client Application.", e)
        }

        clientApp.runLaterControl {
            AppLogger.info("Starting Client Application...")
            clientApp.start()
            val loadSchematic = config.loadSchematic
            AppLogger.trace(s"Applying schematic '${loadSchematic.name}'...")
            loadSchematic.setup(clientApp)
            AppLogger.trace("Schematic applied successfully.")
        }.join() match {
            case Failure(e) =>
                throw new ApplicationInstantiationException("Could not instantiate Client Application.", e)
            case Success(_) =>
                initialized = true
                clientApp
        }
    }
}
