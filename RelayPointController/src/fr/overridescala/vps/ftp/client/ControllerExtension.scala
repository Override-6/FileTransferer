package fr.overridescala.vps.ftp.client

import java.util.Scanner

import fr.overridescala.vps.ftp.api.Relay
import fr.overridescala.vps.ftp.api.task.ext.TaskExtension
import fr.overridescala.vps.ftp.client.auto.AutomationManager
import fr.overridescala.vps.ftp.client.cli.CommandManager
import fr.overridescala.vps.ftp.client.cli.commands._

class ControllerExtension(relay: Relay) extends TaskExtension(relay) {

    private val commandsManager = new CommandManager(new Scanner(System.in))
    private val automationManager = new AutomationManager()

    override def main(): Unit = {
        commandsManager.register("download", TransferCommand.download(relay))
        commandsManager.register("upload", TransferCommand.upload(relay))
        commandsManager.register("crtf", new CreateFileCommand(relay))
        commandsManager.register("ping", new PingCommand(relay))
        commandsManager.register("stress", new StressTestCommand(relay))
        commandsManager.register("exec", new ExecuteUnknownTaskCommand(relay))
        commandsManager.register("delete", new DeleteFileCommand(relay))
        commandsManager.register("listen", new ListenDirCommand(relay, automationManager))
        commandsManager.start()
        automationManager.start()

        val properties = relay.properties
        properties.putProperty("automation_manager", automationManager)
    }
}
