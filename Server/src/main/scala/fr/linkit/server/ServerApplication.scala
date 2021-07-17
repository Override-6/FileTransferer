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

package fr.linkit.server

import fr.linkit.api.connection.{ConnectionInitialisationException, NoSuchConnectionException}
import fr.linkit.api.local.concurrency.{WorkerPools, workerExecution}
import fr.linkit.api.local.resource.external.ResourceFolder
import fr.linkit.api.local.system
import fr.linkit.api.local.system._
import fr.linkit.api.local.system.config.ApplicationInstantiationException
import fr.linkit.api.local.system.security.ConnectionSecurityException
import fr.linkit.engine.local.LinkitApplication
import fr.linkit.engine.local.concurrency.pool.BusyWorkerPool
import fr.linkit.engine.local.system.{EngineConstants, Rules, StaticVersions}
import fr.linkit.server.ServerApplication.Version
import fr.linkit.server.local.config.{ServerApplicationConfigBuilder, ServerApplicationConfiguration, ServerConnectionConfiguration}
import fr.linkit.server.connection.ServerConnection

import scala.collection.mutable
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class ServerApplication private(override val configuration: ServerApplicationConfiguration, resources: ResourceFolder) extends LinkitApplication(configuration, resources) {

    protected override val appPool         = new BusyWorkerPool(configuration.mainPoolThreadCount, "Application")
    private            val serverCache     = mutable.HashMap.empty[Any, ServerConnection]
    private            val securityManager = configuration.securityManager

    override val versions: Versions = StaticVersions(ApiConstants.Version, EngineConstants.Version, Version)

    override def countConnections: Int = {
        /*
         * We need to divide the servers map size by two because servers connections are twice put into this map,
         * once for port association, and once for identifier association.
         */
        serverCache.size / 2
    }

    @workerExecution
    override def shutdown(): Unit = this.synchronized {
        /*
        * This method is synchronized on the current application's instance
        * in order to ensure that multiple shutdown aren't executed
        * on the same application, wich could occur to some problems.
        * */
        appPool.ensureCurrentThreadOwned("Shutdown must be performed into Application's pool")
        ensureAlive()
        AppLogger.info("Server application is shutting down...")

        val totalConnectionCount = countConnections
        var downCount            = 0
        val shutdownTask         = WorkerPools.currentTask

        listConnections.foreach((serverConnection: ServerConnection) => serverConnection.runLater {
            wrapCloseAction(s"Server connection ${serverConnection.currentIdentifier}") {
                serverConnection.shutdown()
            }
            downCount += 1
            if (downCount == totalConnectionCount)
                shutdownTask.wakeup()
        })
        if (countConnections > 0)
            appPool.pauseCurrentTask()

        alive = false
        AppLogger.info("Server application successfully shutdown.")
    }

    override def listConnections: Iterable[ServerConnection] = {
        serverCache.values.toSet
    }

    @workerExecution
    def openServerConnection(configuration: ServerConnectionConfiguration): ServerConnection = /*this.synchronized*/ {
        appPool.ensureCurrentThreadOwned("Open server connection must be performed into Application's pool.")

        ensureAlive()
        if (configuration.identifier.length > Rules.MaxConnectionIDLength)
            throw new IllegalArgumentException(s"Server identifier length > ${Rules.MaxConnectionIDLength}")

        securityManager.checkConnectionConfig(configuration)
        AppLogger.debug("Instantiating server connection...")
        val serverConnection = new ServerConnection(this, configuration)

        serverConnection.runLaterControl {
            AppLogger.debug("Starting server...")
            serverConnection.start()
            AppLogger.debug("Server started !")
        }.derivate() match {
            case Failure(e) => throw new ConnectionInitialisationException(s"Failed to create server connection ${configuration.identifier} on port ${configuration.port}", e)
            case Success(_) =>
        }

        try {
            securityManager.checkConnection(serverConnection)
        } catch {
            case e: ConnectionSecurityException =>
                serverConnection.runLater {
                    serverConnection.shutdown()
                }
                throw e
        }

        val port       = configuration.port
        val identifier = configuration.identifier
        serverCache.put(port, serverConnection)
        serverCache.put(identifier, serverConnection)

        serverConnection
    }

    @throws[NoSuchConnectionException]("If the connection isn't found in the application's cache.")
    def unregister(serverConnection: ServerConnection): Unit = {
        ensureAlive()

        val configuration = serverConnection.configuration
        val port          = configuration.port
        val identifier    = configuration.identifier

        if (!serverCache.contains(port) || !serverCache.contains(identifier))
            throw NoSuchConnectionException(s"Could not unregister server $identifier opened on port $port; connection not found in application context.")

        serverCache.remove(port)
        serverCache.remove(identifier)
    }

    override def getConnection(identifier: String): Option[ServerConnection] = {
        serverCache.get(identifier)
    }

    override def getConnection(port: Int): Option[ServerConnection] = {
        serverCache.get(port)
    }

}

object ServerApplication {

    @volatile private var initialized = false
    val Version: Version = system.Version(name = "Server", "1.0.0", false)

    def launch(config: ServerApplicationConfiguration, otherSources: Class[_]*): ServerApplication = this.synchronized {
        if (initialized)
            throw new IllegalStateException("ServerApplication was already launched !")

        val appResources = LinkitApplication.prepareApplication(Version, config, otherSources)

        val serverAppContext = try {
            new ServerApplication(config, appResources)
        } catch {
            case NonFatal(e) =>
                throw new ApplicationInstantiationException("Could not instantiate Server Application.", e)
        }

        serverAppContext.runLaterControl {
            AppLogger.info("Starting Server Application...")
            serverAppContext.start()
            val loadSchematic = config.loadSchematic
            AppLogger.trace(s"Applying schematic '${loadSchematic.name}'...")
            loadSchematic.setup(serverAppContext)
            AppLogger.trace("Schematic applied successfully.")
        }.join() match {
            case Failure(exception) => throw new ApplicationInstantiationException("Could not instantiate Server Application.", exception)
            case Success(_)         =>
                initialized = true
                serverAppContext
        }
    }

    def launch(builder: ServerApplicationConfigBuilder, caller: Class[_]): ServerApplication = {
        launch(builder.buildConfig(), caller)
    }
}
