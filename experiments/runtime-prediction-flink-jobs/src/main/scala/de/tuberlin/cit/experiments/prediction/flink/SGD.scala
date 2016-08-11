package de.tuberlin.cit.experiments.prediction.flink

import org.apache.flink.api.scala._
import org.apache.flink.ml.common.{LabeledVector, WeightVector}
import org.apache.flink.ml.math.DenseVector
import org.apache.flink.ml.optimization.{GenericLossFunction, LinearPrediction, SimpleGradientDescent, SquaredLoss}

object SGD {
  def main(args: Array[String]) {
    if (args.length != 2) {
      Console.err.println("Usage: SGD <inputPath> <outputPath>")
      System.exit(-1)
    }

    val inputPath = args(0)
    val outputPath = args(1)

    val env = ExecutionEnvironment.getExecutionEnvironment

    val sgd = SimpleGradientDescent()
      .setIterations(50)
      .setStepsize(0.1)
      .setLossFunction(GenericLossFunction(SquaredLoss, LinearPrediction))

    val trainingDataSet = env.readTextFile(inputPath)
      .map(line => {
        val doubles = line.split(' ').map(_.toDouble)
        LabeledVector(doubles.last, DenseVector(doubles.take(doubles.length - 1)))
      })

    val initialWeights: Option[DataSet[WeightVector]] = Option.empty

    val weights: DataSet[WeightVector] = sgd.optimize(trainingDataSet, initialWeights)
    weights.print()
  }
}
