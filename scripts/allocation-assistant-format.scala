import sys.process._
import java.io._


/*
Configuration variable. Adapt these to reflect the paths of the systems and the wally-nodes in use.
*/
final val HADOOP_HOME = sys.env("HADOOP_HOME")
final val FREAMON_HOME = "/home/ilya/freamon" //sys.env("FREAMON_HOME")
final val ALLOCATION_ASSISTANT = "/home/ilya/allocation-assistant/allocation-assistant"
final val CMD = "-c /home/ilya/allocation-assistant/emptyConf.conf -r 800 -n 4 -N 40 -i 22 -m 9000 -s 7 -e spark /home/ilya/allocation-assistant-experiment/spark-sgd-1.0-SNAPSHOT-jar-with-dependencies.jar hdfs://wally072.cit.tu-berlin.de:45010//sgd.txt hdfs://wally072.cit.tu-berlin.de:45010//dummy_out.txt"

val includeRanges = List( (73,115) )
val excludeRanges = List( (95,95),(102,102) )


// compute slaves list from ranges
val includes = includeRanges.foldLeft(Set[Int]())({case (b,r) => b union (r._1 to r._2 toSet)})
val excludes = excludeRanges.foldLeft(Set[Int]())({case (b,r) => b union (r._1 to r._2 toSet)})
val slaves = (includes diff excludes).map(i => f"wally$i%03d").toSeq.sorted

def writeSlavesToFile(slaves: Seq[String], filePath: String) {
  val writer = new PrintWriter(filePath)
  slaves.foreach(slave => writer.println(slave))
  writer.close()
}

def parseScaleOut(s: String): Int = {
  val line = s.split("\n").toList.last
  val so = line.split(" ").last.toInt
  so
}

val slavesPath = s"$HADOOP_HOME/etc/hadoop/slaves"

// stop everything
writeSlavesToFile(slaves, slavesPath)
s"$FREAMON_HOME/sbin/stop-cluster.sh $FREAMON_HOME/myCluster.conf".!
s"$HADOOP_HOME/sbin/stop-dfs.sh".!
s"$HADOOP_HOME/sbin/stop-yarn.sh".!
(s"$HADOOP_HOME/bin/hadoop namenode -format -nonInteractive -force").!
s"pssh -h $slavesPath rm -rf /data/ilya/hdfs/data".!

for (i <- 1 to 21) {

  // delete data
  (s"$HADOOP_HOME/bin/hadoop namenode -format -nonInteractive -force").!
  s"pssh -h $slavesPath rm -rf /data/ilya/hdfs/data".!

  // reupload input data
  s"$HADOOP_HOME/sbin/start-dfs.sh".!
  s"$HADOOP_HOME/sbin/start-yarn.sh".!
  println("Uploading to HDFS...")
  s"$HADOOP_HOME/bin/hadoop fs -copyFromLocal /data/ilya/datasets/sgd.txt /".!

  s"$FREAMON_HOME/sbin/start-cluster.sh $FREAMON_HOME/myCluster.conf".!

  Thread.sleep(5000) // wait some time

  // run allocation-assistant
  s"$ALLOCATION_ASSISTANT $CMD".!

  // shutdown all
  s"$FREAMON_HOME/sbin/stop-cluster.sh $FREAMON_HOME/myCluster.conf".!
  s"$HADOOP_HOME/sbin/stop-yarn.sh".!
  s"$HADOOP_HOME/sbin/stop-dfs.sh".!
}
