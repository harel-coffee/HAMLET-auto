package org.queueinc.hamlet.automl

import org.queueinc.hamlet.controller.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*
import java.util.stream.Stream
import kotlin.random.Random

private val containerName = "automl-container_" + Random.nextLong(0, Long.MAX_VALUE)

fun getOutputFromProgram(program: Array<String>) {
    val proc = Runtime.getRuntime().exec(program)

    println("Here is the standard output/error of the command:\n")

    Stream.of(proc.errorStream, proc.inputStream).parallel().forEach { isForOutput: InputStream ->
        try {
            BufferedReader(InputStreamReader(isForOutput)).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    println(line)
                }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    proc.waitFor()
    proc.destroy()
}

fun stopAutoML() {
    val stop = arrayOf("docker", "stop", containerName)
    val rm = arrayOf("docker", "rm", containerName)

    getOutputFromProgram(stop)
    getOutputFromProgram(rm)
}

fun runAutoML(workspacePath: String, debug: Boolean) {

    if (debug) {
        val build = arrayOf("docker", "build", "-t", "automl-container", ".")
        val run = arrayOf("docker", "run", "--name", containerName,
            "--volume", "${workspacePath}:/home/workspace", "--detach", "-t", "automl-container")

        getOutputFromProgram(build)
        getOutputFromProgram(run)

        return
    }

    val version = Properties().let {
        it.load(Controller::class.java.getResourceAsStream("/version.properties"))
        it.getProperty("version")
    }

    val run = arrayOf("docker", "run", "--name", containerName,
        "--volume", "${workspacePath}:/home/workspace", "--detach", "-t", "ghcr.io/queueinc/automl-container:$version")

    getOutputFromProgram(run)
}

fun execAutoML(config: Config) {

    val exec  =
        arrayOf("docker", "exec", containerName, "python", "automl/main.py",
                "--dataset", config.dataset, "--metric", config.metric, "--mode", config.mode, "--batch_size", config.batchSize.toString(), "--time_budget", config.timeBudget.toString(), "--seed", config.seed.toString(),
                "--input_path", "/home/workspace/automl/input/automl_input_${config.iteration}.json",
                "--output_path", "/home/workspace/automl/output/automl_output_${config.iteration}.json")

    getOutputFromProgram(exec)

    println("AutoML execution ended")
}