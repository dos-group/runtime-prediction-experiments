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

import java.io.Serializable;
import java.util.Iterator;
import java.util.Random;

/**
 * Generates data for the LinearRegression program.
 */
public class LinearRegressionDataGenerator implements Iterator<double[]>, Serializable {

	private Random random;

	private int count;
	private int numDataPoints;


	public LinearRegressionDataGenerator(int numDataPoints) {
		this.numDataPoints = numDataPoints;
		this.count = 0;
		this.random = new Random();
	}

	public void setRandom(Random random) {
		this.random = random;
	}

	@Override
	public boolean hasNext() {
		return count < numDataPoints;
	}

	@Override
	public double[] next() {
		++count;
		double[] point = new double[2];
		point[0] = random.nextGaussian();
		point[1] = 2 * point[0] + 0.01 * random.nextGaussian();
		return point;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
}
