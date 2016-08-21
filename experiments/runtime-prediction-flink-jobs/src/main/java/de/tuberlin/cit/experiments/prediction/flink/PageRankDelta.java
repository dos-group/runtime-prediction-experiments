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
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.operators.DeltaIteration;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.core.fs.FileSystem;

/**
 * An implementation of the page rank algorithm, using native iteration and deltas.
 *
 * Based on http://data-artisans.com/data-analysis-with-flink-a-case-study-and-tutorial/
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
public class PageRankDelta extends AbstractPageRank {

	private static final double DAMPENING_FACTOR = 0.85;

	// *************************************************************************
	//     PROGRAM
	// *************************************************************************
	
	public static void main(String... args) throws Exception {
		
		if(!parseParameters(args)) {
			return;
		}


		ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		DataSet<Tuple2<Long, Long>> links = env.readTextFile(linksPath).filter(new FilterComment()).map(new DirectEdge());

		// assign initial rank to pages
		DataSet<Tuple2<Long, Double>> pagesWithRanks = links.groupBy(0).reduceGroup(new RankAssigner(1.0d)); // 1.0d / numPages ?
		DataSet<Tuple2<Long, Double>> initialDeltas = pagesWithRanks.map(new InitialDeltaBuilder(numPages));

		// build adjacency list from link input
		DataSet<Tuple2<Long, Long[]>> adjacencyListInput =
				links.groupBy(0).reduceGroup(new BuildOutgoingEdgeList());

		DeltaIteration<Tuple2<Long, Double>, Tuple2<Long, Double>> adaptiveIteration = pagesWithRanks.iterateDelta(initialDeltas, maxIterations, 0);

		adaptiveIteration.setSolutionSetUnManaged(!adaptiveIteration.isSolutionSetUnManaged());

		DataSet<Tuple2<Long, Double>> deltas = adaptiveIteration.getWorkset()
				.join(adjacencyListInput).where(0).equalTo(0).with(new DeltaDistributorAndStat(DAMPENING_FACTOR))
				.groupBy(0)
				.reduceGroup(new AggAndFilterStat(threshold));

		DataSet<Tuple2<Long, Double>> rankUpdates = adaptiveIteration.getSolutionSet()
				.join(deltas).where(0).equalTo(0).with(new SolutionJoinAndStat());

		DataSet<Tuple2<Long, Double>> finalPageRanks = adaptiveIteration.closeWith(rankUpdates, deltas);

		// emit result
		finalPageRanks.writeAsCsv(outputPath, "\n", " ", FileSystem.WriteMode.OVERWRITE);

		// execute program
		try {
			env.execute("Page Rank Iteration with Deltas");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// *************************************************************************
	//     USER FUNCTIONS
	// *************************************************************************


	// *************************************************************************
	//     UTIL METHODS
	// *************************************************************************
	
	private static String linksPath = null;
	private static String outputPath = null;
	private static long numPages = 0;
	private static int maxIterations = 10;
	private static double threshold = 0.1;
	
	private static boolean parseParameters(String[] programArguments) {
		if(programArguments.length >= 4) {
			linksPath = programArguments[0];
			outputPath = programArguments[1];
			numPages = Long.parseLong(programArguments[2]);
			maxIterations = Integer.parseInt(programArguments[3]);

			if (programArguments.length == 5) {
				threshold = Double.parseDouble(programArguments[4]);
			}
		} else {
			System.err.println("Usage: PageRank <links path> " +
					"<result path> <num pages> <max number of iterations> [threshold]");
			return false;
		}

		return true;
	}

	@Override
	public String getDescription() {
		return "Parameters: <links-path> <result-path> <num-pages> <max-number-of-iterations> [threshold]";
	}

}
