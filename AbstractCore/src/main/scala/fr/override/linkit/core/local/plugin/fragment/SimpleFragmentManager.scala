package fr.`override`.linkit.core.local.plugin.fragment

import fr.`override`.linkit.api.local.plugin.fragment.{FragmentManager, PluginFragment}
import fr.`override`.linkit.api.local.plugin.{LinkitPlugin, Plugin, PluginLoadException}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

class SimpleFragmentManager() extends FragmentManager {

    private val fragmentMap: mutable.Map[Class[_ <: LinkitPlugin], ExtensionFragments] = mutable.Map.empty

    override def putFragment(fragment: PluginFragment)(implicit owner: Plugin): Unit = {
        val pluginClass = owner.getClass
        val fragmentClass = fragment.getClass
        if (getFragment(pluginClass, fragmentClass).isDefined)
            throw new IllegalArgumentException("This fragment kind is already set for this extension")

        fragmentMap.getOrElseUpdate(pluginClass, new ExtensionFragments)
                .putFragment(fragment)

        fragment match {
            case remote: LinkitRemoteFragment =>
                initRemote(remote)

            case _ =>
        }

    }

    override def getFragment[F <: LinkitPluginFragment](extensionClass: Class[_ <: Plugin], fragmentClass: Class[F]): Option[F] = {
        val fragmentsOpt = fragmentMap.get(extensionClass)
        if (fragmentsOpt.isEmpty)
            return None

        fragmentsOpt
                .get
                .getFragment(fragmentClass)
    }

    def listRemoteFragments(): List[LinkitRemoteFragment] = {
        val fragments = ListBuffer.empty[LinkitPluginFragment]
        fragmentMap.values
                .foreach(_.list()
                        .foreach(fragments.addOne))
        fragments.filter(_.isInstanceOf[LinkitRemoteFragment])
                .map(_.asInstanceOf[LinkitRemoteFragment])
                .toList
    }

    private[plugin] def startFragments(): Int = {
        var count = 0
        fragmentMap.values.foreach(fragments => {
            count += fragments.startAll()
        })
        count
    }

    private[plugin] def startFragments(extensionClass: Class[_ <: LinkitPlugin]): Unit = {
        fragmentMap.get(extensionClass).foreach(_.startAll())
    }

    private[plugin] def destroyFragments(): Unit = {
        fragmentMap.values.foreach(_.destroyAll())
    }

    private class ExtensionFragments {
        private val fragments: mutable.Map[Class[_ <: LinkitPluginFragment], LinkitPluginFragment] = mutable.Map.empty

        def getFragment[F <: LinkitPluginFragment](fragmentClass: Class[F]): Option[F] = {
            fragments.get(fragmentClass).asInstanceOf[Option[F]]
        }

        def putFragment(fragment: LinkitPluginFragment): Unit = {
            fragments.put(fragment.getClass, fragment)
        }

        def startAll(): Int = {
            for (fragment <- Map.from(fragments).values) {
                try {
                    fragment.start()
                } catch {
                    case NonFatal(e) =>
                        fragments.remove(fragment.getClass)
                        throw PluginLoadException(s"Could not start fragment : Exception thrown while starting it", e)
                }
            }
            //Notifying the network that some remote fragments where added.
            val names = fragments.values
                    .filter(_.isInstanceOf[LinkitRemoteFragment])
                    .map(_.asInstanceOf[LinkitRemoteFragment].nameIdentifier)
                    .toArray
            if (names.length > 0) {
                //network.notifyLocalRemoteFragmentSet(names)
            }

            fragments.size
        }

        /**
         * Fragments must be destroyed only once the relay is closed.
         * */
        def destroyAll(): Unit = {
            fragments.values.foreach(_.destroy())
        }

        def list(): Iterable[LinkitPluginFragment] = {
            fragments.values
        }

    }

}