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


package de.tuberlin.cit.experiments.prediction.datagen.flink.linreg;

import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.io.TextOutputFormat;
import org.apache.flink.core.fs.FileSystem;

import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Random;

/**
 * Generates data for the LinearRegression program.
 */
public class LinearRegressionDataGeneratorJob {

	static {
		Locale.setDefault(Locale.US);
	}

	private static final String POINTS_FILE = "data";
	private static final long DEFAULT_SEED = 4650285087650871364L;
	private static final int DIMENSIONALITY = 1;
	private static final DecimalFormat FORMAT = new DecimalFormat("#0.00");
	private static final char DELIMITER = ' ';

	/**
	 * Main method to generate data for the LinearRegression program.
	 * <p/>
	 * The generator creates to files:
	 * <ul>
	 * <li><code>{tmp.dir}/data</code> for the data points
	 * </ul>
	 *
	 * @param args
	 * <ol>
	 * <li>Int: Number of data points
	 * <li><b>Optional</b> Long: Random seed
	 * </ol>
	 */
	public static void main(String[] args) throws Exception {

		// check parameter count
		if (args.length < 1) {
			System.out.println("LinearRegressionDataGenerator <numberOfDataPoints> [<seed>]");
			System.exit(1);
		}

		// parse parameters
		final int numDataPoints = Integer.parseInt(args[0]);
		final long firstSeed = args.length > 1 ? Long.parseLong(args[4]) : DEFAULT_SEED;
		final Random random = new Random(firstSeed);
		final String tmpDir = System.getProperty("java.io.tmpdir");

		LinearRegressionDataGenerator generator = new LinearRegressionDataGenerator(numDataPoints);
		generator.setRandom(random);

		// write the points out
		ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		env
				.fromCollection(generator, double[].class)
				.writeAsFormattedText(Paths.get(tmpDir, POINTS_FILE).toString(), FileSystem.WriteMode.OVERWRITE,
						new TextOutputFormat.TextFormatter<double[]>() {
							@Override
							public String format(double[] data) {
								StringBuilder builder = new StringBuilder();
								for (int j = 0; j < data.length; j++) {
									builder.append(FORMAT.format(data[j]));
									if (j < data.length - 1) {
										builder.append(DELIMITER);
									}
								}
								return builder.toString();
							}
						});

		env.execute("Linear Regression Data Generator");

		System.out.println("Wrote " + numDataPoints + " data points to " + Paths.get(tmpDir, POINTS_FILE));
	}


}
