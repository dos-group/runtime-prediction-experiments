/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package de.tuberlin.cit.experiments.prediction.datagen.flink.kmeans;

import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.io.CollectionInputFormat;
import org.apache.flink.api.java.io.IteratorInputFormat;
import org.apache.flink.api.java.io.TextInputFormat;
import org.apache.flink.api.java.io.TextOutputFormat;
import org.apache.flink.api.java.operators.DataSource;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.core.fs.FileSystem;

import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * Generates data for the KMeans program.
 */
public class KMeansDataGeneratorJob {

	static {
		Locale.setDefault(Locale.US);
	}

	private static final String CENTERS_FILE = "centers";
	private static final String POINTS_FILE = "points";
	private static final long DEFAULT_SEED = 4650285087650871364L;
	private static final double DEFAULT_VALUE_RANGE = 100.0;
	private static final double RELATIVE_STDDEV = 0.08;
	private static final int DIMENSIONALITY = 2;
	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#0.00");
	private static final char DELIMITER = ' ';

	/**
	 * Main method to generate data for the KMeans program.
	 * <p/>
	 * The generator creates to files:
	 * <ul>
	 * <li><code>&lt; output-path &gt;/points</code> for the data points
	 * <li><code>&lt; output-path &gt;/centers</code> for the cluster centers
	 * </ul>
	 *
	 * @param args
	 * <ol>
	 * <li>Int: Number of data points
	 * <li>Int: Number of cluster centers
	 * <li><b>Optional</b> String: Output path, default value is {tmp.dir}
	 * <li><b>Optional</b> Double: Standard deviation of data points
	 * <li><b>Optional</b> Double: Value range of cluster centers
	 * <li><b>Optional</b> Long: Random seed
	 * </ol>
	 */
	public static void main(String[] args) throws Exception {

		// check parameter count
		if (args.length < 2) {
			System.out.println("KMeansDataGenerator -points <num> -k <num clusters> [-output <output-path>] [-stddev <relative stddev>] [-range <centroid range>] [-seed <seed>] [-skew <factorsPerCenter>]");
			System.exit(1);
		}

		// parse parameters

		final ParameterTool params = ParameterTool.fromArgs(args);
		final long numDataPoints = params.getLong("points");
		final int k = params.getInt("k");
		final String outDir = params.get("output", System.getProperty("java.io.tmpdir"));
		final double stddev = params.getDouble("stddev", RELATIVE_STDDEV);
		final double range = params.getDouble("range", DEFAULT_VALUE_RANGE);
		final long firstSeed = params.getLong("seed", DEFAULT_SEED);

		final String skewString = params.get("skew");

		int[] skew = parseSkewFactors(skewString, k);

		final double absoluteStdDev = stddev * range;

		final Random random = new Random();
		random.setSeed(firstSeed);

		// the means around which data points are distributed
		final KMeansCentroid[] means = uniformRandomCenters(random, k, DIMENSIONALITY, range);

		MeanGeneratorConfiguration[] confs = new MeanGeneratorConfiguration[means.length];
		for (int i = 0; i < means.length; i++) {
			confs[i] = new MeanGeneratorConfiguration(means[i].getCoordinates(), absoluteStdDev);
		}

		int skewSum = 0;
		for (int s : skew) {
			skewSum += s;
		}

		double[] meanProbs = new double[skew.length];
		for (int i = 0; i < skew.length; i++) {
			meanProbs[i] = skew[i] / (double) skewSum;
		}


		ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		final TextOutputFormat.TextFormatter<double[]> pointsFormatter = new TextOutputFormat.TextFormatter<double[]>() {
			@Override
			public String format(double[] coordinates) {
				StringBuilder builder = new StringBuilder();
				for (int j = 0; j < coordinates.length; j++) {
					builder.append(DECIMAL_FORMAT.format(coordinates[j]));
					if (j < coordinates.length - 1) {
						builder.append(DELIMITER);
					}
				}
				return builder.toString();
			}
		};

		final TextOutputFormat.TextFormatter<KMeansCentroid> centersFormatter = new TextOutputFormat.TextFormatter<KMeansCentroid>() {
			@Override
			public String format(KMeansCentroid centroid) {
				StringBuilder builder = new StringBuilder();

				builder.append(NumberFormat.getIntegerInstance().format(centroid.getId()));
				builder.append(DELIMITER);
				builder.append(pointsFormatter.format(centroid.getCoordinates()));

				return builder.toString();
			}
		};

		// write the points out
		KMeansDataGenerator kMeansDataGenerator = new KMeansDataGenerator(confs, meanProbs, numDataPoints);
		kMeansDataGenerator.setRandom(random);
		env
				.fromParallelCollection(kMeansDataGenerator, double[].class)
				.writeAsFormattedText(outDir + "/" + POINTS_FILE, FileSystem.WriteMode.OVERWRITE, pointsFormatter);

		env.execute("KMeans Data Generator: Points data");
		System.out.println("Wrote " + numDataPoints + " data points to " + Paths.get(outDir, POINTS_FILE));

		KMeansCentroid[] centers = uniformRandomCenters(random, k, DIMENSIONALITY, range);
		env
				.fromCollection(Arrays.asList(centers))
				.writeAsFormattedText(outDir + "/" + CENTERS_FILE, FileSystem.WriteMode.OVERWRITE, centersFormatter);


		env.execute("KMeans Data Generator: Centroids data");
		System.out.println("Wrote " + k + " cluster centers to " + Paths.get(outDir, CENTERS_FILE));
	}

	private static int[] parseSkewFactors(String skewString, int k) {
		int[] skew = new int[k];
		if (skewString != null && skewString.split(",").length == k) {
			String[] skewFactors = skewString.split(",");
			for (int i = 0; i < k; i++) {
				skew[i] = Integer.valueOf(skewFactors[i]);
			}
		} else {
			for (int i = 0; i < k; i++) {
				skew[i] = 1;
			}
		}
		return skew;
	}

	private static KMeansCentroid[] uniformRandomCenters(Random rnd, int num, int dimensionality, double range) {
		final double halfRange = range / 2;

		KMeansCentroid[] centers = new KMeansCentroid[num];

		for (int i = 0; i < num; i++) {
			double[] coordinates = new double[dimensionality];
			for (int dim = 0; dim < dimensionality; dim++) {
				coordinates[dim] = (rnd.nextDouble() * range) - halfRange;
			}
			centers[i] = new KMeansCentroid(i, coordinates);
		}
		return centers;
	}

}
