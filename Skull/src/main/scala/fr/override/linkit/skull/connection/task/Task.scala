package fr.`override`.linkit.skull.connection.task

import java.util.concurrent.atomic.AtomicReference

import org.jetbrains.annotations.Nullable


//TODO reedit the DOC
/**
 * <p>
 *     Task is a abstract task, all Tasks (excepted Completers) have to extends this class to be considered as a
 *     self-executable task.
 * </p>
 * <p>
 *     TasksCompleters does not have specific Class or Trait to extends, they just have to extend the TaskExecutor.
 *     TasksCompleters are created by the [[TaskCompleterHandler]], and are normally not instantiable from other classes.
 *     TasksCompleters, are the tasks which completes the self-executable tasks.
 *      @example
 *          in [[CreateFileTask]], the self-executable (the class that directly extends from [[Task]]) will ask to the targeted Relay
 *          if it could creates a file located on the specified path.
 *          The targeted Relay will instantiate / execute the Completer of [[CreateFileTask]], in which the file will be created.
 * </p>
 * <p>
 *      This class is a member of [[TaskAction]] and [[TaskExecutor]].
 *      [[TaskAction]] is a Trait given to the user. this class only have enqueue and complete methods.
 *      [[TaskExecutor]] is a Trait used by [[TasksHandler]] which will invoke TaskExecutor#execute nor TaskExecutor#sendTaskInfo if this task instance
 *      was created by the program (!TaskCompleters)
 * </p>
 * @tparam T the return type of this Task when successfully executed
 *
 * @see [[TasksHandler]]
 * @see [[TaskAction]]
 * @see [[TaskExecutor]]
 * */

trait Task[T] extends TaskExecutor with TaskAction[T] {

    /**
     * Invoked by TaskExecutors to signal that this task was unsuccessful
     * */
    protected def fail(msg: String): Unit

    /**
     * Invoked by TaskExecutors to signal that this task was successful
     * */
    protected def success(t: T): Unit

}
