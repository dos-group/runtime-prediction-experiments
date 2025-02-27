package de.tuberlin.cit.experiments.prediction.flink.util.clustering;

/**
 * A simple two-dimensional centroid, basically a point with an ID.
 */
public class Centroid extends Point {

    public int id;

    public Centroid() {}

    public Centroid(int id, double x, double y) {
        super(x,y);
        this.id = id;
    }

    public Centroid(int id, Point p) {
        super(p.x, p.y);
        this.id = id;
    }

    @Override
    public String toString() {
        return id + " " + super.toString();
    }
}
