package de.tuberlin.cit.experiments.prediction.cli.command

import java.lang.{System => Sys}
import java.nio.file.{Path, Paths}

import com.typesafe.config.Config
import net.sourceforge.argparse4j.inf.{Namespace, Subparser}
import org.peelframework.core.cli.command.Command
import org.peelframework.core.config.loadConfig
import org.peelframework.core.util.shell
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service

/**
  * Creates a plot from dstat logs.
  */
@Service("dstat:plot")
class DstatResultPlot extends Command {
  override val name: String = "dstat:plot"

  override val help: String = "create a plot for statistics gathered using dstat"

  var beanName: String = ""
  var outputFolderPath: String = ""
  var plotString: String = ""
  var suiteName: String = ""

  override def register(parser: Subparser): Unit = {
    parser.addArgument("--bean")
      .`type`(classOf[String])
      .dest("app.dstat.plot.beanName")
      .metavar("BEAN")
      .help("the dstat bean name (default \"dstat-0.7.2\")")

    parser.addArgument("--output")
      .`type`(classOf[String])
      .dest("app.dstat.plot.output")
      .metavar("OUTPUT")
      .help("the output folder for the dstat plots (default \"results/dstat/$beanName/plots\")")

    parser.addArgument("dstatPlotString")
      .`type`(classOf[String])
      .dest("app.dstat.plot.cmd")
      .metavar("COLUMN")
      .help("the plots to be generated")

    parser.addArgument("suiteName")
      .`type`(classOf[String])
      .dest("app.suite.name")
      .metavar("SUITE")
      .help("the name of the suite")

  }

  override def configure(ns: Namespace): Unit = {
    val config: Config = loadConfig()


    beanName = ns.getString("app.dstat.plot.beanName")
    if (beanName == null) {
      beanName = "dstat-0.7.2"
    }

    outputFolderPath = ns.getString("app.dstat.plot.output")
    if (outputFolderPath == null) {
      outputFolderPath = Paths.get(config.getString("app.path.results"), "dstat", beanName, "plots").toString
    }

    plotString = ns.getString("app.dstat.plot.cmd")
    suiteName = ns.getString("app.suite.name")


    val dstatToolsFolderPath: String = Paths.get(config.getString("app.path.utils"), "dstat-tools").toString

    if (!Paths.get(dstatToolsFolderPath, "dstat_plot.rb").toFile.exists) {
      logger.info("Downloading dstat-tools...")
      shell.rmDir(dstatToolsFolderPath)
      shell.ensureFolderIsWritable(Paths.get(dstatToolsFolderPath))
      shell ! s"git clone https://github.com/citlab/dstat-tools $dstatToolsFolderPath"
      logger.info("Downloaded dstat-tools successfully.")
    }
  }

  def dstatPlotCmd: String = {
    val config: Config = loadConfig()
    val utilsPath: String = config.getString("app.path.utils")
    Paths.get(utilsPath, "dstat-tools", "dstat_plot.rb").toString
  }

  def runPlot(categories: String, field: String): Unit = {

    val config = loadConfig()
    val suiteFolder = Paths.get(config.getString("app.path.results"), suiteName).toFile
    if (!suiteFolder.exists || suiteFolder.isFile) {
      logger.error(s"No results found in ${suiteFolder.getAbsolutePath}")
      return
    }
    val runFolderPaths = suiteFolder.listFiles
      .filter(_.isDirectory)
      .map(_.getAbsolutePath)
      .sorted

    shell.ensureFolderIsWritable(Paths.get(outputFolderPath))

    for (runFolderPath <- runFolderPaths) {
      val dstatLogsFolder = Paths.get(runFolderPath, "logs", "dstat", beanName).toFile

      if (dstatLogsFolder.exists) {

        val dstatCsvFilePaths = dstatLogsFolder.listFiles
          .filter(_.getName.endsWith(".csv"))
          .map(_.getAbsolutePath)
          .sorted
          .mkString(" ")

        val outputPath: Path = Paths.get(outputFolderPath, Paths.get(runFolderPath).getFileName + ".png")
        val cmd = s"""ruby $dstatPlotCmd -c "$categories" -f "$field" -o "$outputPath" $dstatCsvFilePaths """
        logger.info("Generating Plots...")
        logger.debug(cmd)
        shell ! cmd
        logger.info(s"Plots generated in $outputPath")

      }

    }
  }

  override def run(context: ApplicationContext): Unit = {
    val cmdString = plotString
    cmdString.split("\\|") match {
      case Array(categories, field) => runPlot(categories.split("_").mkString(" "), field)
      case _ => throw new RuntimeException("Invalid parameters")
    }
  }

}
