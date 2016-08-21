package de.tuberlin.cit.experiments.prediction.flink;

import org.apache.flink.api.common.functions.CoGroupFunction;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.GroupReduceFunction;
import org.apache.flink.api.common.functions.IterationRuntimeContext;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichFlatJoinFunction;
import org.apache.flink.api.common.functions.RichJoinFunction;
import org.apache.flink.api.java.functions.FunctionAnnotation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;

import java.util.Iterator;

public abstract class AbstractConnectedComponents {

	/**
	 * Function that turns a value into a 2-tuple where both fields are that value.
	 */
	@FunctionAnnotation.ForwardedFields("*->f0")
	public static final class DuplicateValue<T> implements MapFunction<T, Tuple2<T, T>> {

		@Override
		public Tuple2<T, T> map(T vertex) {
			return new Tuple2<T, T>(vertex, vertex);
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
	 * Function that initial the connected components with its own id.
	 */
	public static final class InitialValue implements GroupReduceFunction<Tuple2<Integer, Integer>, Tuple2<Integer, Integer>> {

		@Override
		public void reduce(Iterable<Tuple2<Integer, Integer>> t, Collector<Tuple2<Integer, Integer>> c) throws Exception {
			Integer v = t.iterator().next().f0;
			c.collect(new Tuple2<Integer, Integer>(v, v));
		}
	}

	/**
	 * Undirected edges by emitting for each input edge the input edges itself and an inverted
	 * version.
	 */
	public static final class UndirectEdge implements FlatMapFunction<String, Tuple2<Integer, Integer>> {

		@Override
		public void flatMap(String edge, Collector<Tuple2<Integer, Integer>> out) {
			String[] line = edge.split("\t");
			Integer v1 = Integer.parseInt(line[0]);
			Integer v2 = Integer.parseInt(line[1]);
			out.collect(new Tuple2<Integer, Integer>(v1, v2));
			out.collect(new Tuple2<Integer, Integer>(v2, v1));
		}
	}



	/**
	 * UDF that joins a (Vertex-ID, Component-ID) pair that represents the current component that a
	 * vertex is associated with, with a (Source-Vertex-ID, Target-VertexID) edge. The function
	 * produces a (Target-vertex-ID, Component-ID) pair.
	 */
	@FunctionAnnotation.ForwardedFieldsFirst("f1->f1")
	@FunctionAnnotation.ForwardedFieldsSecond("f1->f0")
	public static final class NeighborWithComponentIDJoin extends
			RichJoinFunction<Tuple2<Integer, Integer>, Tuple2<Integer, Integer>, Tuple2<Integer, Integer>> {

		private long inCount;
		private long outCount;

		@Override
		public void open(Configuration parameters) throws Exception {
			inCount = 0;
			outCount = 0;
		}

		@Override
		public Tuple2<Integer, Integer> join(Tuple2<Integer, Integer> vertexWithComponent, Tuple2<Integer, Integer> edge) {
			inCount++;
			outCount++;
			return new Tuple2<Integer, Integer>(edge.f1, vertexWithComponent.f1);
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

			getRuntimeContext().getLongCounter("NeighborWithComponentIDJoin-iter_" + iteration + "-subtask_" + subtask + "-in").add(inCount);
			getRuntimeContext().getLongCounter("NeighborWithComponentIDJoin-iter_" + iteration + "-subtask_" + subtask + "-out").add(outCount);
		}
	}

	@FunctionAnnotation.ForwardedFieldsFirst("*")
	public static final class ComponentIdFilter extends
			RichFlatJoinFunction<Tuple2<Integer, Integer>, Tuple2<Integer, Integer>, Tuple2<Integer, Integer>> {

		private long inCount;
		private long outCount;

		@Override
		public void open(Configuration parameters) throws Exception {
			inCount = 0;
			outCount = 0;
		}

		@Override
		public void join(Tuple2<Integer, Integer> candidate, Tuple2<Integer, Integer> old, Collector<Tuple2<Integer, Integer>> out) {
			inCount++;

			if (candidate.f1 < old.f1) {
				out.collect(candidate);
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

			getRuntimeContext().getLongCounter("ComponentIdFilter-iter_"+iteration+"-subtask_" + subtask + "-in").add(inCount);
			getRuntimeContext().getLongCounter("ComponentIdFilter-iter_"+iteration+"-subtask_" + subtask + "-out").add(outCount);
		}
	}

	public static final class UpdateSolutionSet implements
			CoGroupFunction<Tuple2<Integer, Integer>, Tuple2<Integer, Integer>, Tuple2<Integer, Integer>> {

		@Override
		public void coGroup(Iterable<Tuple2<Integer, Integer>> ss, Iterable<Tuple2<Integer, Integer>> delta, Collector<Tuple2<Integer, Integer>> c) throws Exception {
			Iterator<Tuple2<Integer, Integer>> it1 = ss.iterator();
			Iterator<Tuple2<Integer, Integer>> it2 = delta.iterator();
			if (it2.hasNext()) {
				c.collect(it2.next());
			} else {
				c.collect(it1.next());
			}
		}
	}
}
