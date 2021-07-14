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

package fr.linkit.engine.connection.cache.repo.tree

import fr.linkit.api.connection.cache.repo.description.{TreeViewBehavior, WrapperBehavior}
import fr.linkit.api.connection.cache.repo.tree.SyncNode
import fr.linkit.api.connection.cache.repo.{Chip, PuppetWrapper, Puppeteer}
import fr.linkit.engine.connection.cache.repo.NoSuchPuppetException
import fr.linkit.engine.connection.cache.repo.invokation.local.ObjectChip
import fr.linkit.engine.connection.cache.repo.invokation.remote.InvocationPacket
import fr.linkit.engine.connection.packet.UnexpectedPacketException
import fr.linkit.engine.connection.packet.fundamental.RefPacket
import fr.linkit.engine.connection.packet.traffic.channel.request.ResponseSubmitter
import org.jetbrains.annotations.Nullable

import java.util.concurrent.ThreadLocalRandom
import scala.collection.mutable
import scala.reflect.ClassTag

class PuppetNode[A](override val puppeteer: Puppeteer[A], //Remote invocation
                    override val chip: Chip[A], //Reflective invocation
                    val descriptions: TreeViewBehavior,
                    val isOwner: Boolean,
                    id: Int,
                    @Nullable override val parent: SyncNode[_]) extends MemberSyncNode[A] {

    private   val ownerID: String = puppeteer.ownerID
    /**
     * This seq contains all the fields synchronized of the object
     * */
    protected val members         = new mutable.HashMap[Int, SyncNode[_]]

    override def addChild(id: Int, node: this.type => SyncNode[_]): Unit = members.put(id, node(this))

    override def getChild[B](id: Int): Option[SyncNode[B]] = members.get(id) match {
        case None        => None
        case Some(value) => value match {
            case node: PuppetNode[B] => Some(node)
            case _                   => None
        }
    }

    override def getChildren: Map[Int, SyncNode[_]] = members.toMap

    override def getID: Int = id

    override def handlePacket(packet: InvocationPacket, response: ResponseSubmitter): Unit = {
        if (!(packet.path sameElements treeViewPath)) {
            val packetPath = packet.path
            if (!packetPath.startsWith(treeViewPath))
                throw UnexpectedPacketException("Received invocation packet that does not target this node or this node's children.")

            getGrandChild(packetPath.drop(treeViewPath.length))
                    .fold(throw new NoSuchPuppetException(s"Received packet that aims for an unknown puppet children node (${packetPath.mkString("$", " -> ", "")}")) {
                        case node: MemberSyncNode[_] => node.handlePacket(packet, response)
                        case _                       =>
                    }
        }
        makeMemberInvocation(packet, response)
    }

    private def makeMemberInvocation(packet: InvocationPacket, response: ResponseSubmitter): Unit = {
        val methodID = packet.methodID
        var result   = chip.callMethod(methodID, packet.params)
        if (isOwner) {
            val methodBehavior = puppeteer.wrapperBehavior.getMethodBehavior(methodID)
            val canSyncReturnType = methodBehavior.get.syncReturnValue
            if (result != null && canSyncReturnType) {
                implicit val resultCT: ClassTag[_] = ClassTag(result.getClass)
                val id             = ThreadLocalRandom.current().nextInt()
                val synchronizer   = puppeteer.repo
                val resultNodePath = treeViewPath ++ Array(id)

                result = synchronizer.genSynchronizedObject(resultNodePath, result, ownerID, descriptions) {
                    (wrapper, path) =>
                        val id          = path.last
                        val description = descriptions.getFromClass(wrapper.getClass)
                        getGrandChild(path.drop(treeViewPath.length).dropRight(1))
                                .fold(throw new NoSuchPuppetException(s"Puppet Node not found in path ${path.mkString("$", " -> ", "")}")) {
                                    parent =>
                                        val chip      = ObjectChip[Any](ownerID, description.asInstanceOf[WrapperBehavior[Any]], wrapper.asInstanceOf[PuppetWrapper[Any]])
                                        val puppeteer = wrapper.getPuppeteer.asInstanceOf[Puppeteer[Any]]
                                        parent.addChild(id, new PuppetNode(puppeteer, chip, descriptions, isOwner, id, _))
                                }

                }
            }
            response
                    .addPacket(RefPacket[Any](result))
                    .submit()
        }
    }
}
