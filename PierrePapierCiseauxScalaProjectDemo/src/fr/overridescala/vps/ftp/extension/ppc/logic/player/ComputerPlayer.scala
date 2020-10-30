package fr.overridescala.vps.ftp.`extension`.ppc.logic.player

import java.util.concurrent.ThreadLocalRandom

import fr.overridescala.vps.ftp.`extension`.ppc.logic.MoveType

//noinspection RemoveRedundantReturn
class ComputerPlayer extends Player {

    private val randomizer = ThreadLocalRandom.current();

    override def getName: String = s"ordinateur"

    override def play(): MoveType = {
        val plays = MoveType.values()
        val moveTypeIndex = randomizer.nextInt(plays.length)
        return plays(moveTypeIndex)
    }
}