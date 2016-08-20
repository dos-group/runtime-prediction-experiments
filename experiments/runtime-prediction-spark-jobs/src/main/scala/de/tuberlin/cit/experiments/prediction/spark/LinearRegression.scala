/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// scalastyle:off println
package de.tuberlin.cit.experiments.prediction.spark

import org.apache.log4j.{Level, Logger}

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.mllib.optimization.SquaredL2Updater
import org.apache.spark.mllib.regression.LinearRegressionWithSGD
import org.apache.spark.mllib.util.MLUtils

/**
 * An example app for linear regression. Run with
 * {{{
 * bin/run-example org.apache.spark.examples.mllib.LinearRegression
 * }}}
 * A synthetic dataset can be found at `data/mllib/sample_linear_regression_data.txt`.
 * If you use it as a template to create your own app, please use `spark-submit` to submit your app.
 */
object LinearRegression {

//  object RegType extends Enumeration {
//    type RegType = Value
//    val NONE, L1, L2 = Value
//  }

  def main(args: Array[String]) {

    val numIterations = 100
    val stepSize = 1.0
    val regParam= 0.01

    if (args.length < 1) {
      System.err.println("Usage: ConnectedComponent <input_path>")
      System.exit(1)
    }

    val conf = new SparkConf().setAppName(s"LinearRegression")
    val sc = new SparkContext(conf)

    Logger.getRootLogger.setLevel(Level.WARN)

    //    val training = MLUtils.loadLibSVMFile(sc, params.input).cache()

    val input = args(0)

    val training = MLUtils.loadLabeledPoints(sc, input).cache()

    //    val splits = examples.randomSplit(Array(0.8, 0.2))
    //    val training = splits(0).cache()
    //    val test = splits(1).cache()

    //    val numTraining = training.count()
    //    val numTest = test.count()
    //    println(s"Training: $numTraining, test: $numTest.")

    training.unpersist(blocking = false)

    val updater = new SquaredL2Updater()

    val algorithm = new LinearRegressionWithSGD()
    algorithm.optimizer
      .setNumIterations(numIterations)
      .setStepSize(stepSize)
      .setUpdater(updater)
      .setRegParam(regParam)

    algorithm.run(training)

    //    model.weights
    //
    //    val prediction = model.predict(test.map(_.features))
    //    val predictionAndLabel = prediction.zip(test.map(_.label))

    //    val loss = predictionAndLabel.map { case (p, l) =>
    //      val err = p - l
    //      err * err
    //    }.reduce(_ + _)
    //    val rmse = math.sqrt(loss / numTest)

    //    println(s"Test RMSE = $rmse.")

    sc.stop()

  }

//  def run(params: Params): Unit = {
//
//  }
}
// scalastyle:on println
