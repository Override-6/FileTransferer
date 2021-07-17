package fr.linkit.api.connection.cache.repo.tree

import fr.linkit.api.connection.cache.repo.{Chip, Puppeteer}
import org.jetbrains.annotations.Nullable

import scala.collection.mutable.ListBuffer

trait SyncNode[A] {

    lazy val treeViewPath: Array[Int] = {
        var parent: SyncNode[_] = this
        val buff                = ListBuffer.empty[Int]
        while (parent != null) {
            buff.insert(0, parent.getID)
            parent = parent.parent
        }
        buff.toArray
    }
    val puppeteer: Puppeteer[A]

    val chip: Chip[A]

    def getID: Int

    @Nullable val parent: SyncNode[_]

    def getChild[B](id: Int): Option[SyncNode[B]]

    def addChild(id: Int, factory: this.type => SyncNode[_]): Unit

    def getGrandChild(relativePath: Array[Int]): Option[SyncNode[_]] = {
        var child: SyncNode[_] = this
        for (childID <- relativePath) {
            val opt = child.getChild(childID)
            if (opt.isEmpty)
                return None
            child = opt.get
        }
        Option(child)
    }
}
