package de.tuberlin.cit.experiments.prediction.datagen.flink.kmeans;

public class KMeansCentroid {

	private int id;
	private double[] coordinates;

	public KMeansCentroid(int id, double[] coordinates) {
		this.id = id;
		this.coordinates = coordinates;
	}

	public double[] getCoordinates() {
		return this.coordinates;
	}

	public int getId() {
		return id;
	}
}
