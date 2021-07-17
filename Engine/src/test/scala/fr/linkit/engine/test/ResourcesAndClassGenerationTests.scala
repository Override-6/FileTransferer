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

package fr.linkit.engine.test

import fr.linkit.api.connection.cache.repo.description.PuppeteerInfo
import fr.linkit.api.connection.cache.repo.{InvocationChoreographer, PuppetWrapper}
import fr.linkit.api.connection.packet.DedicatedPacketCoordinates
import fr.linkit.api.local.generation.TypeVariableTranslator
import fr.linkit.api.local.resource.external.ResourceFolder
import fr.linkit.api.local.system.config.ApplicationConfiguration
import fr.linkit.api.local.system.fsa.FileSystemAdapter
import fr.linkit.api.local.system.security.ApplicationSecurityManager
import fr.linkit.api.local.system.{AppLogger, Version}
import fr.linkit.engine.connection.cache.repo.CacheRepoContent
import fr.linkit.engine.connection.cache.repo.DefaultEngineObjectCenter.PuppetProfile
import fr.linkit.engine.connection.cache.repo.description.annotation.AnnotationBasedMemberBehaviorFactory
import fr.linkit.engine.connection.cache.repo.description.{SimplePuppetClassDescription, SimpleWrapperBehavior, TreeViewDefaultBehavior}
import fr.linkit.engine.connection.cache.repo.generation.{CloneHelper, PuppetWrapperClassGenerator, WrappersClassResource}
import fr.linkit.engine.connection.cache.repo.invokation.remote.InstancePuppeteer
import fr.linkit.engine.connection.packet.fundamental.RefPacket.AnyRefPacket
import fr.linkit.engine.connection.packet.serialization.{DefaultSerializer, FSTSerializer}
import fr.linkit.engine.connection.packet.traffic.channel.request.ResponsePacket
import fr.linkit.engine.local.LinkitApplication
import fr.linkit.engine.local.generation.compilation.access.DefaultCompilerCenter
import fr.linkit.engine.local.resource.external.LocalResourceFolder._
import fr.linkit.engine.local.system.fsa.LocalFileSystemAdapters
import fr.linkit.engine.local.utils.ScalaUtils
import fr.linkit.engine.test.ScalaReflectionTests.TestClass
import fr.linkit.engine.test.classes.ScalaClass
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api._
import org.mockito.Mockito

import java.util
import java.util.concurrent.ThreadLocalRandom
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.reflect.runtime.universe.TypeTag

@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(classOf[OrderAnnotation])
class ResourcesAndClassGenerationTests {

    private var resources: ResourceFolder    = _
    private val app      : LinkitApplication = Mockito.mock(classOf[LinkitApplication])

    @BeforeAll
    def init(): Unit = {
        val config      = new ApplicationConfiguration {
            override val pluginFolder   : Option[String]             = None
            override val resourceFolder : String                     = System.getenv("LinkitHome")
            override val fsAdapter      : FileSystemAdapter          = LocalFileSystemAdapters.Nio
            override val securityManager: ApplicationSecurityManager = null
        }
        val testVersion = Version("Tests", "0.0.0", false)
        System.setProperty("LinkitImplementationVersion", testVersion.toString)

        resources = LinkitApplication.prepareApplication(testVersion, config, Seq(getClass))
        Mockito.when(app.getAppResources).thenReturn(resources)
        Mockito.when(app.compilerCenter).thenReturn(new DefaultCompilerCenter)
        LinkitApplication.setInstance(app)
        AppLogger.useVerbose = true
    }

    @Test
    @Order(-1)
    def genericParameterTests(): Unit = {
        val testMethod  = classOf[TestClass].getMethod("genericMethod2")
        val javaResult  = TypeVariableTranslator.toJavaDeclaration(testMethod.getTypeParameters)
        val scalaResult = TypeVariableTranslator.toScalaDeclaration(testMethod.getTypeParameters)
        println(s"Java result = ${javaResult}")
        println(s"Scala result = ${scalaResult}")
    }

    @Test
    def packetTest(): Unit = InvocationChoreographer.forceLocalInvocation {
        val wrapper = forObject(new util.ArrayList[String]())

        val packet          = ArrayBuffer(DedicatedPacketCoordinates(12, "s1", "TestServer1"), ResponsePacket(7, Array(AnyRefPacket(Some(CacheRepoContent(Array(PuppetProfile(Array(0), wrapper, "TestServer1"))))))))
        val testPacketBytes = new FSTSerializer().serialize(packet, true)
        println(s"Serialized testedPacket : ${ScalaUtils.toPresentableString(testPacketBytes)}")
        val rePacket = Assertions.assertInstanceOf(packet.getClass, new FSTSerializer().deserialize(testPacketBytes))
        println(s"resulting packet = ${rePacket.mkString("Array(", ", ", ")")}")

    }

    @Test
    @Order(0)
    def methodsSignatureTests(): Unit = {
        val clazz = classOf[TestClass]
        println(s"clazz = ${clazz}")
        val methods = clazz.getDeclaredMethods
        methods.foreach { method =>
            val args = method.getGenericParameterTypes
            println(s"args = ${args.mkString("Array(", ", ", ")")}")
        }
        println("qsd")
    }

    @Test
    @Order(2)
    def generateSimpleClass(): Unit = {
        val obj = forObject(new ScalaClass)
        println(s"obj = ${obj}")
        obj.testRMI()
        obj.b = "Le sexe"
        println(s"ojb.b = ${obj.b}")
    }

    private def getListBuff(): ListBuffer[String] = {
        if (ThreadLocalRandom.current().nextBoolean())
            new ListBufferImpl()
        else new ListBuffer()
    }

    @Test
    def wtfIsGoingOn(): Unit = {
        println("Dumb implementation : ")
        val test: ListBuffer[String] = getListBuff()
        test.addOne("TETSFT")
    }

    @Test
    @Order(3)
    def generateComplexScalaClass(): Unit = InvocationChoreographer.forceLocalInvocation {
        val obj = forObject(ListBuffer.empty[String])

        val serial = new DefaultSerializer()
        val bytes = serial.serialize(obj, true)
        println(s"ScalaUtils.toPresentableString(bytes) = ${ScalaUtils.toPresentableString(bytes)}")
        val result = serial.deserialize(bytes)
        println(s"result = ${result}")
    }

    class ListBufferImpl extends ListBuffer[String] {

        override def addOne(elem: String): ListBufferImpl.this.type = {
            println("LOL")
            //super.addOne(elem)
            this
        }
    }

    @Test
    @Order(3)
    def generateComplexJavaClass(): Unit = {
        forObject(LocalFileSystemAdapters.Nio)
    }

    def forObject[A: TypeTag](obj: A): A with PuppetWrapper[A] = {
        Assertions.assertNotNull(resources)
        val cl = obj.getClass.asInstanceOf[Class[A]]

        val resource    = resources.getOrOpenThenRepresent[WrappersClassResource](LinkitApplication.getProperty("compilation.working_dir.classes"))
        val generator   = new PuppetWrapperClassGenerator(new DefaultCompilerCenter, resource)
        val puppetClass = generator.getPuppetClass[A](cl)
        println(s"puppetClass = ${puppetClass}")
        val factory = AnnotationBasedMemberBehaviorFactory()
        val pup     = new InstancePuppeteer[A](null, null, PuppeteerInfo("", 8, "", Array(1)), SimpleWrapperBehavior(SimplePuppetClassDescription[A](cl), new TreeViewDefaultBehavior(factory)))
        val puppet  = CloneHelper.instantiateFromOrigin[A](puppetClass, obj)

        puppet.initPuppeteer(pup)
        puppet.getChoreographer.forceLocalInvocation {
            println(s"puppet = ${puppet}")
            println(s"puppet.getWrappedClass = ${puppet.getWrappedClass}")
        }
        puppet
    }

}

