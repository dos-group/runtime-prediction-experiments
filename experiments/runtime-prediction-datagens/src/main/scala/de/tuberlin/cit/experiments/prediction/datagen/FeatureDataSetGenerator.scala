package de.tuberlin.cit.experiments.prediction.datagen

import org.apache.flink.api.scala._
import org.apache.flink.core.fs.FileSystem

import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.math.pow

object FeatureDataSetGenerator {
  def main(args: Array[String]) {
    if (args.length != 3) {
      Console.err.println("Usage: DataSetGenerator <points> <dimension> <outputPath>")
      System.exit(-1)
    }

    val m = args(0).toInt
    val n = args(1).toInt
    val outputPath = args(2)

    val env = ExecutionEnvironment.getExecutionEnvironment

    env
      .generateSequence(1, m)
      .map(_ => {
        val x = ThreadLocalRandom.current().nextDouble()
        val noise = ThreadLocalRandom.current().nextGaussian()

        // generate the function value with added gaussian noise
        val label = function(x) + noise

        // generate a vandermatrix from x
        val vector = polyvander(x, n - 1)

        label + "," + vector.mkString(" ")
      })
      .writeAsText(outputPath, FileSystem.WriteMode.OVERWRITE)

    env.execute("Data Set Generator")
  }

  def polyvander(x: Double, order: Int): Array[Double] = {
    (0 to order).map(pow(x, _)).toArray
  }

  def function(x: Double): Double = {
    2 * x + 10
  }
}
