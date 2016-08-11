package de.tuberlin.cit.experiments.prediction.datagen.flink.kmeans;

import java.io.Serializable;

public class MeanGeneratorConfiguration implements Serializable {
	private double[] mean;
	private double stdDev;

	public MeanGeneratorConfiguration(double[] mean, double stdDev) {
		this.mean = mean;
		this.stdDev = stdDev;
	}

	public double[] getMean() {
		return mean;
	}

	public double getStdDev() {
		return stdDev;
	}
}
