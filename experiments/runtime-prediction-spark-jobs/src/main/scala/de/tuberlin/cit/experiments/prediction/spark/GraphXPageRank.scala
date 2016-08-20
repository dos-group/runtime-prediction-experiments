/*
 * Connected Component workload for BigDataBench
 */
package de.tuberlin.cit.experiments.prediction.spark

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.graphx._

object GraphXPageRank {

  def main(args: Array[String]) {

    if (args.length < 3) {
      System.err.println("Usage: ConnectedComponent <input_path>" +
        "<output_path> + <num_iterations>")
      System.exit(1)
    }

    val conf = new SparkConf().setAppName("Graph X PageRank")
    val spark = new SparkContext(conf)

    val inputFilename = args(0)
    val outputFilename = args(1)
    val numIterations = args(2).toInt

    val graph = GraphLoader.edgeListFile(spark, inputFilename)
    val pr = graph.staticPageRank(numIterations)

    pr.vertices.saveAsTextFile(outputFilename)

    System.exit(0)
  }

}
