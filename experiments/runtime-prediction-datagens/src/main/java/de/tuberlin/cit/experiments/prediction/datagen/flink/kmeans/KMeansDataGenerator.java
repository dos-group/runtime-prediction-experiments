package de.tuberlin.cit.experiments.prediction.datagen.flink.kmeans;

import org.apache.flink.util.SplittableIterator;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Random;

public class KMeansDataGenerator extends SplittableIterator<double[]> implements Serializable {

	private MeanGeneratorConfiguration[] configurations;
	private double[] meanProbabilities;

	private long generatedCounter;
	private long numDataPoints;

	private Random rnd;

	public KMeansDataGenerator(MeanGeneratorConfiguration[] configurations, double[] meanProbabilities,
			long numDataPoints) {
		this.configurations = configurations;
		this.meanProbabilities = meanProbabilities;
		this.numDataPoints = numDataPoints;
		this.generatedCounter = 0;
		this.rnd = new Random();
	}

	private MeanGeneratorConfiguration getRandomMean() {
		double meanProb = rnd.nextDouble();

		int selectedMean = meanProbabilities.length - 1;

		double s = 0;
		for (int i = 0; i < meanProbabilities.length; i++) {
			s += meanProbabilities[i];
			if (meanProb < s) {
				selectedMean = i;
				break;
			}
		}

		return configurations[selectedMean];
	}

	private double[] generateRandomPoint(MeanGeneratorConfiguration conf) {
		double[] mean = conf.getMean();
		double stdDev = conf.getStdDev();

		double[] point = new double[mean.length];
		for (int i = 0; i < mean.length; i++) {
			point[i] = rnd.nextGaussian() * stdDev + mean[i];
		}

		return point;
	}

	public void setRandom(Random rnd) {
		this.rnd = rnd;
	}

	@Override
	public double[] next() {
		++generatedCounter;
		return generateRandomPoint(getRandomMean());
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean hasNext() {
		return generatedCounter < numDataPoints;
	}

	@Override
	public Iterator<double[]>[] split(int numPartitions) {
		KMeansDataGenerator[] iters = new KMeansDataGenerator[numPartitions];

		for (int i=0; i<numPartitions; i++) {
			iters[i] = new KMeansDataGenerator(configurations, meanProbabilities, numDataPoints/numPartitions);
		}

		return iters;
	}

	@Override
	public int getMaximumNumberOfSplits() {
		return Integer.MAX_VALUE < numDataPoints ? Integer.MAX_VALUE : (int) numDataPoints;
	}
}
