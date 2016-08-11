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


package de.tuberlin.cit.experiments.prediction.flink;

import de.tuberlin.cit.experiments.prediction.flink.shared.AbstractPageRank;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.ProgramDescription;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.GroupReduceFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.aggregation.Aggregations;
import org.apache.flink.api.java.functions.FunctionAnnotation.ForwardedFields;
import org.apache.flink.api.java.operators.IterativeDataSet;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * An implementation of the page rank algorithm, using native iteration without deltas.
 *
 * Based on Flink PageRankBasic example.
 *
 * <p>
 * Input files are plain text files and must be formatted as follows:
 * <ul>
 * <li>Edges are represented as pairs for vertex IDs which are separated by tab
 * characters. Edges are separated by new-line characters.<br>
 * For example <code>"1 2\n2 12\n1 12\n42 63"</code> gives four (undirected) edges (1)-(2), (2)-(12), (1)-(12), and (42)-(63).
 * </ul>
 * 
 */
@SuppressWarnings("serial")
public class PageRank implements ProgramDescription {

	private static final double DAMPENING_FACTOR = 0.85;
	private static final double EPSILON = 0.0001;

	// *************************************************************************
	//     PROGRAM
	// *************************************************************************
	
	public static void main(String... args) throws Exception {
		
		if(!parseParameters(args)) {
			return;
		}

		ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		DataSet<Tuple2<Long, Long>> links = env.readTextFile(linksPath).filter(new FilterComment()).map(new AbstractPageRank.DirectEdge());

		// assign initial rank to pages
		DataSet<Tuple2<Long, Double>> pagesWithRanks = links.groupBy(0).reduceGroup(new RankAssigner(1.0d)); // 1.0d / numPages ?

		// build adjacency list from link input
		DataSet<Tuple2<Long, Long[]>> adjacencyListInput =
				links.groupBy(0).reduceGroup(new BuildOutgoingEdgeList());

		// set iterative data set
		IterativeDataSet<Tuple2<Long, Double>> iteration = pagesWithRanks.iterate(maxIterations);

		DataSet<Tuple2<Long, Double>> newRanks = iteration
				// join pages with outgoing edges and distribute rank
				.join(adjacencyListInput).where(0).equalTo(0).flatMap(new JoinVertexWithEdgesMatchStats())
				// collect and sum ranks
				.groupBy(0).aggregate(Aggregations.SUM, 1)
				// apply dampening factor
				.map(new Dampener(DAMPENING_FACTOR, numPages));

		DataSet<Tuple2<Long, Double>> finalPageRanks = iteration.closeWith(
				newRanks,
				newRanks.join(iteration).where(0).equalTo(0)
						// termination condition
						.filter(new EpsilonFilter()));

		// Statistics
//		long count = pagesWithRanks.count();
//		System.err.println("Found " + count + " pages in dataset.");

		// emit result
		finalPageRanks.writeAsCsv(outputPath, "\n", " ", FileSystem.WriteMode.OVERWRITE);

		// execute program
		try {
			JobExecutionResult result = env.execute("Page Rank Iteration without Deltas");
			Map<String, Object> accumulators = result.getAllAccumulatorResults();
			List<String> keys = new ArrayList<String>(accumulators.keySet());
			Collections.sort(keys);
			System.out.println("Accumulators:");
			for (String key : keys) {
				System.out.println(key + " : " + accumulators.get(key));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// *************************************************************************
	//     USER FUNCTIONS
	// *************************************************************************

	/**
	 * A map function that assigns an initial rank to all pages.
	 */
	public static final class RankAssigner implements GroupReduceFunction<Tuple2<Long, Long>, Tuple2<Long, Double>>  {
		Tuple2<Long, Double> outPageWithRank;

		public RankAssigner(double rank) {
			this.outPageWithRank = new Tuple2<Long, Double>(-1l, rank);
		}

		@Override
		public void reduce(Iterable<Tuple2<Long, Long>> values, Collector<Tuple2<Long, Double>> out) throws Exception {
			outPageWithRank.f0 = values.iterator().next().f0;
			out.collect(outPageWithRank);
		}
	}

	/**
	 * A reduce function that takes a sequence of edges and builds the adjacency list for the vertex where the edges
	 * originate. Run as a pre-processing step.
	 */
	@ForwardedFields("0")
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

	/**
	 * Join function that distributes a fraction of a vertex's rank to all neighbors.
	 */
	public static final class JoinVertexWithEdgesMatch implements FlatMapFunction<Tuple2<Tuple2<Long, Double>, Tuple2<Long, Long[]>>, Tuple2<Long, Double>> {

		@Override
		public void flatMap(Tuple2<Tuple2<Long, Double>, Tuple2<Long, Long[]>> value, Collector<Tuple2<Long, Double>> out){
			Long[] neigbors = value.f1.f1;
			double rank = value.f0.f1;
			double rankToDistribute = rank / ((double) neigbors.length);

			for (int i = 0; i < neigbors.length; i++) {
				out.collect(new Tuple2<Long, Double>(neigbors[i], rankToDistribute));
			}
		}
	}

	public static final class JoinVertexWithEdgesMatchStats extends RichFlatMapFunction<Tuple2<Tuple2<Long, Double>, Tuple2<Long, Long[]>>, Tuple2<Long, Double>> {
		long inCount;
		long outCount;

		@Override
		public void open(Configuration parameters) throws Exception {
			inCount = 0;
			outCount = 0;
			System.err.println("Starting map iteration " + getIterationRuntimeContext().getSuperstepNumber() + " at subtask " + getIterationRuntimeContext().getIndexOfThisSubtask());
		}

		@Override
		public void flatMap(Tuple2<Tuple2<Long, Double>, Tuple2<Long, Long[]>> value, Collector<Tuple2<Long, Double>> out){
			Long[] neighbors = value.f1.f1;
			double rank = value.f0.f1;
			double rankToDistribute = rank / ((double) neighbors.length);

			inCount++;
			for (int i = 0; i < neighbors.length; i++) {
				out.collect(new Tuple2<Long, Double>(neighbors[i], rankToDistribute));
				outCount++;
			}
		}

		@Override
		public void close() throws Exception {
			int subtask = getIterationRuntimeContext().getIndexOfThisSubtask();
			int iteration = getIterationRuntimeContext().getSuperstepNumber();
			getRuntimeContext().getLongCounter("map-iter_"+iteration+"-subtask_" + subtask + "-in").add(inCount);
			getRuntimeContext().getLongCounter("map-iter_"+iteration+"-subtask_" + subtask + "-out").add(outCount);
		}
	}

	/**
	 * The function that applies the page rank dampening formula
	 */
	@ForwardedFields("0")
	public static final class Dampener implements MapFunction<Tuple2<Long,Double>, Tuple2<Long,Double>> {

		private final double dampening;
		private final double randomJump;

		public Dampener(double dampening, double numVertices) {
			this.dampening = dampening;
			this.randomJump = (1 - dampening) / numVertices;
		}

		@Override
		public Tuple2<Long, Double> map(Tuple2<Long, Double> value) {
			value.f1 = (value.f1 * dampening) + randomJump;
			return value;
		}
	}

	/**
	 * Filter that filters vertices where the rank difference is below a threshold.
	 */
	public static final class EpsilonFilter implements FilterFunction<Tuple2<Tuple2<Long, Double>, Tuple2<Long, Double>>> {

		@Override
		public boolean filter(Tuple2<Tuple2<Long, Double>, Tuple2<Long, Double>> value) {
			return Math.abs(value.f0.f1 - value.f1.f1) > EPSILON;
		}
	}
	
	// *************************************************************************
	//     UTIL METHODS
	// *************************************************************************
	
	private static String linksPath = null;
	private static String outputPath = null;
	private static long numPages = 0;
	private static int maxIterations = 10;
	
	private static boolean parseParameters(String[] programArguments) {

		// parse input arguments
		if(programArguments.length == 4) {
			linksPath = programArguments[0];
			outputPath = programArguments[1];
			numPages = Long.parseLong(programArguments[2]);
			maxIterations = Integer.parseInt(programArguments[3]);
		} else {
			System.err.println("Usage: PageRank <links path> " +
					"<result path> <num pages> <max number of iterations>");
			return false;
		}

		return true;
	}

	@Override
	public String getDescription() {
		return "Parameters: <links-path> <result-path> <num-pages> <max-number-of-iterations>";
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
}
