package fr.linkit.api.connection.cache.repo.generation

import fr.linkit.api.connection.cache.repo.PuppetWrapper
import fr.linkit.api.local.generation.PuppetClassDescription

import scala.reflect.runtime.universe
import scala.reflect.{ClassTag, classTag}

/**
 * This class generates a class that extends
 * */
trait PuppetWrapperGenerator {

    def getPuppetClass[S](clazz: Class[S]): Class[S with PuppetWrapper[S]]

    def getPuppetClass[S](desc: PuppetClassDescription[S]): Class[S with PuppetWrapper[S]]

    def getClass[S: universe.TypeTag : ClassTag]: Class[S with PuppetWrapper[S]] = getPuppetClass[S](classTag[S].runtimeClass.asInstanceOf[Class[S]])

    def preGenerateDescs(descriptions: Seq[PuppetClassDescription[_]]): Unit

    def preGenerateClasses(classes: Seq[Class[_]]): Unit

    def isClassGenerated[T: ClassTag]: Boolean = isWrapperClassGenerated(classTag[T].runtimeClass)

    def isWrapperClassGenerated[T](clazz: Class[T]): Boolean

    def isClassGenerated[S <: PuppetWrapper[S]](clazz: Class[S]): Boolean
}
