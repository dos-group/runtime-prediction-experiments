package de.tuberlin.cit.experiments.prediction.flink.shared;

import org.apache.flink.api.common.ProgramDescription;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.GroupReduceFunction;
import org.apache.flink.api.common.functions.IterationRuntimeContext;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichFlatJoinFunction;
import org.apache.flink.api.common.functions.RichGroupReduceFunction;
import org.apache.flink.api.common.functions.RichJoinFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.java.functions.FunctionAnnotation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;

import java.util.ArrayList;

public abstract class AbstractPageRank implements ProgramDescription {

	/**
	 * A map function that assigns an initial rank to all pages.
	 */
	public static final class RankAssigner implements GroupReduceFunction<Tuple2<Long, Long>, Tuple2<Long, Double>> {
		private final double rank;

		public RankAssigner(double rank) {
			this.rank = rank;
		}

		@Override
		public void reduce(Iterable<Tuple2<Long, Long>> values, Collector<Tuple2<Long, Double>> out) throws Exception {
			long id = values.iterator().next().f0;
			out.collect(new Tuple2<Long, Double>(id, rank));
		}
	}

	/**
	 * A reduce function that takes a sequence of edges and builds the adjacency list for the vertex where the edges
	 * originate. Run as a pre-processing step.
	 */
	@FunctionAnnotation.ForwardedFields("0")
	public static final class BuildOutgoingEdgeList implements GroupReduceFunction<Tuple2<Long, Long>, Tuple2<Long, Long[]>> {

		private final ArrayList<Long> neighbors = new ArrayList<Long>();

		@Override
		public void reduce(Iterable<Tuple2<Long, Long>> values, Collector<Tuple2<Long, Long[]>> out) {
			neighbors.clear();
			Long id = 0L;

			for (Tuple2<Long, Long> n : values) {
				id = n.f0;
				neighbors.add(n.f1);
			}

			out.collect(new Tuple2<Long, Long[]>(id, neighbors.toArray(new Long[neighbors.size()])));
		}
	}

	@FunctionAnnotation.ForwardedFields("0")
	public static final class InitialDeltaBuilder implements MapFunction<Tuple2<Long, Double>, Tuple2<Long, Double>> {

		private final double uniformRank;

		public InitialDeltaBuilder(long numVertices) {
			this.uniformRank = 1.0 / numVertices;
		}

		@Override
		public Tuple2<Long, Double> map(Tuple2<Long, Double> initialRank) {
			initialRank.f1 -= uniformRank;
			return initialRank;
		}
	}

	public static final class DeltaDistributorAndStat extends RichFlatJoinFunction<Tuple2<Long, Double>, Tuple2<Long, Long[]>, Tuple2<Long, Double>> {

		private final Tuple2<Long, Double> tuple = new Tuple2<Long, Double>();

		private final double dampeningFactor;

		private long inCount;
		private long outCount;

		public DeltaDistributorAndStat(double dampeningFactor) {
			this.dampeningFactor = dampeningFactor;
		}

		@Override
		public void open(Configuration parameters) throws Exception {
			inCount = 0;
			outCount = 0;
		}

		@Override
		public void join(Tuple2<Long, Double> deltaFromPage, Tuple2<Long, Long[]> neighbors, Collector<Tuple2<Long, Double>> out) {
			Long[] targets = neighbors.f1;

			double deltaPerTarget = dampeningFactor * deltaFromPage.f1 / targets.length;

			tuple.f1 = deltaPerTarget;
			for (long target : targets) {
				tuple.f0 = target;
				out.collect(tuple);
			}

			inCount++;
			outCount += targets.length;
		}

		@Override
		public void close() throws Exception {
			int subtask = getRuntimeContext().getIndexOfThisSubtask();
			String iteration;

			if (getRuntimeContext() instanceof IterationRuntimeContext) {
				iteration = getIterationRuntimeContext().getSuperstepNumber() + "";
			} else {
				iteration = "%d";
			}

			getRuntimeContext().getLongCounter("joinDeltaDistributor-iter_"+iteration+"-subtask_" + subtask + "-in").add(inCount);
			getRuntimeContext().getLongCounter("joinDeltaDistributor-iter_"+iteration+"-subtask_" + subtask + "-out").add(outCount);
		}
	}

	@FunctionAnnotation.ForwardedFields("0")
	public static final class AggAndFilter implements GroupReduceFunction<Tuple2<Long, Double>, Tuple2<Long, Double>> {

		private final double threshold;

		public AggAndFilter(double threshold) {
			this.threshold = threshold;
		}

		@Override
		public void reduce(Iterable<Tuple2<Long, Double>> values, Collector<Tuple2<Long, Double>> out)  {
			Long key = null;
			double delta = 0.0;

			for (Tuple2<Long, Double> t : values) {
				key = t.f0;
				delta += t.f1;
			}

			if (Math.abs(delta) > threshold) {
				out.collect(new Tuple2<Long, Double>(key, delta));
			}
		}
	}

	@FunctionAnnotation.ForwardedFields("0")
	public static final class AggAndFilterStat extends RichGroupReduceFunction<Tuple2<Long, Double>, Tuple2<Long, Double>> {

		private final double threshold;
		private long inCount;
		private long outCount;

		public AggAndFilterStat(double threshold) {
			this.threshold = threshold;
		}

		@Override
		public void open(Configuration parameters) throws Exception {
			inCount = 0;
			outCount = 0;
		}

		@Override
		public void reduce(Iterable<Tuple2<Long, Double>> values, Collector<Tuple2<Long, Double>> out)  {
			Long key = null;
			double delta = 0.0;

			for (Tuple2<Long, Double> t : values) {
				key = t.f0;
				delta += t.f1;
				inCount++;
			}

			if (Math.abs(delta) > threshold) {
				out.collect(new Tuple2<Long, Double>(key, delta));
				outCount++;
			}
		}

		@Override
		public void close() throws Exception {
			int subtask = getRuntimeContext().getIndexOfThisSubtask();
			String iteration;

			if (getRuntimeContext() instanceof IterationRuntimeContext) {
				iteration = getIterationRuntimeContext().getSuperstepNumber() + "";
			} else {
				iteration = "%d";
			}

			getRuntimeContext().getLongCounter("reduceAggAndFilter-iter_"+iteration+"-subtask_" + subtask + "-in").add(inCount);
			getRuntimeContext().getLongCounter("reduceAggAndFilter-iter_"+iteration+"-subtask_" + subtask + "-out").add(outCount);
		}
	}

	@FunctionAnnotation.ForwardedFieldsFirst("0")
	@FunctionAnnotation.ForwardedFieldsSecond("0")
	public static final class SolutionJoinAndStat extends RichJoinFunction<Tuple2<Long, Double>, Tuple2<Long, Double>, Tuple2<Long, Double>> {
		private long inCount;
		private long outCount;

		@Override
		public void open(Configuration parameters) throws Exception {
			inCount = 0;
			outCount = 0;
		}

		@Override
		public Tuple2<Long, Double> join(Tuple2<Long, Double> page, Tuple2<Long, Double> delta) {
			page.f1 += delta.f1;
			inCount++;
			outCount++;
			return page;
		}

		@Override
		public void close() throws Exception {
			int subtask = getRuntimeContext().getIndexOfThisSubtask();
			String iteration;

			if (getRuntimeContext() instanceof IterationRuntimeContext) {
				iteration = getIterationRuntimeContext().getSuperstepNumber() + "";
			} else {
				iteration = "%d";
			}

			getRuntimeContext().getLongCounter("joinSolution-iter_"+iteration+"-subtask_" + subtask + "-in").add(inCount);
			getRuntimeContext().getLongCounter("joinSolution-iter_"+iteration+"-subtask_" + subtask + "-out").add(outCount);
		}
	}


	/**
	 * Function that filter out the comment lines.
	 */
	public static final class FilterComment implements FilterFunction<String> {

		@Override
		public boolean filter(String s) throws Exception {
			return !s.startsWith("%");
		}
	}

	/**
	 * Undirected edges by emitting for each input edge the input edges itself and an inverted
	 * version.
	 */
	public static final class DirectEdge implements MapFunction<String, Tuple2<Long, Long>> {

		@Override
		public Tuple2<Long, Long> map(String edge) throws Exception {
			String[] line = edge.split("\t");
			Long v1 = Long.parseLong(line[0]);
			Long v2 = Long.parseLong(line[1]);
			return new Tuple2<Long, Long>(v1, v2);
		}
	}

	/**
	 * Undirected edges by emitting for each input edge the input edges itself and an inverted
	 * version.
	 */
	public static final class UndirectEdge implements FlatMapFunction<String, Tuple2<Long, Long>> {

		@Override
		public void flatMap(String edge, Collector<Tuple2<Long, Long>> out) {
			String[] line = edge.split("\t");
			Long v1 = Long.parseLong(line[0]);
			Long v2 = Long.parseLong(line[1]);
			out.collect(new Tuple2<Long, Long>(v1, v2));
			out.collect(new Tuple2<Long, Long>(v2, v1));
		}
	}

	public static final class RecordsCounter<T> extends RichMapFunction<T, T> {
		private final String aggName;
		private long records;

		public RecordsCounter(String aggName) {
			this.aggName = aggName;
		}

		@Override
		public void open(Configuration parameters) throws Exception {
			this.records = 0;
		}

		@Override
		public T map(T record) throws Exception {
			this.records++;
			return record;
		}

		@Override
		public void close() throws Exception {
			getRuntimeContext().getLongCounter(aggName).add(this.records);
		}
	}
}
