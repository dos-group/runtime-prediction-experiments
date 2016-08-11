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

import de.tuberlin.cit.experiments.prediction.flink.shared.AbstractConnectedComponents;
import de.tuberlin.cit.experiments.prediction.flink.util.AccumulatorUtils;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.ProgramDescription;
import org.apache.flink.api.common.operators.base.JoinOperatorBase;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.aggregation.Aggregations;
import org.apache.flink.api.java.operators.DeltaIteration;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.core.fs.FileSystem;

/**
 * An implementation of the connected components algorithm, using a delta iteration.
 * 
 * <p>
 * Initially, the algorithm assigns each vertex an unique ID. In each step, a vertex picks the minimum of its own ID and its
 * neighbors' IDs, as its new ID and tells its neighbors about its new ID. After the algorithm has completed, all vertices in the
 * same component will have the same ID.
 * 
 * <p>
 * A vertex whose component ID did not change needs not propagate its information in the next step. Because of that,
 * the algorithm is easily expressible via a delta iteration. We here model the solution set as the vertices with
 * their current component ids, and the workset as the changed vertices. Because we see all vertices initially as
 * changed, the initial workset and the initial solution set are identical. Also, the delta to the solution set
 * is consequently also the next workset.<br>
 * 
 * <p>
 * Input files are plain text files and must be formatted as follows:
 * <ul>
 * <li>Vertices represented as IDs and separated by new-line characters.<br> 
 * For example <code>"1\n2\n12\n42\n63"</code> gives five vertices (1), (2), (12), (42), and (63).
 * <li>Edges are represented as pairs for vertex IDs which are separated by space 
 * characters. Edges are separated by new-line characters.<br>
 * For example <code>"1 2\n2 12\n1 12\n42 63"</code> gives four (undirected) edges (1)-(2), (2)-(12), (1)-(12), and (42)-(63).
 * </ul>
 * 
 * <p>
 * Usage: <code>ConnectedComponents &lt;vertices path&gt; &lt;edges path&gt; &lt;result path&gt; &lt;max number of iterations&gt;</code><br>
 * If no parameters are provided, the program is run with default data from {@link ConnectedComponentsData} and 10 iterations. 
 * 
 * <p>
 * This example shows how to use:
 * <ul>
 * <li>Delta Iterations
 * <li>Generic-typed Functions 
 * </ul>
 */
@SuppressWarnings("serial")
public class ConnectedComponentsDelta extends AbstractConnectedComponents implements ProgramDescription {
	
	// *************************************************************************
	//     PROGRAM
	// *************************************************************************
	
	public static void main(String... args) throws Exception {
		
		if(!parseParameters(args)) {
			return;
		}

		ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		DataSet<Tuple2<Integer, Integer>> edges = env.readTextFile(edgesPath).filter(new FilterComment()).flatMap(new UndirectEdge());

		// assign the initial components (equal to the vertex id)
		DataSet<Tuple2<Integer, Integer>> verticesWithInitialId = edges.groupBy(0).reduceGroup(new InitialValue());

		DeltaIteration<Tuple2<Integer, Integer>, Tuple2<Integer, Integer>> iteration =
				verticesWithInitialId.iterateDelta(verticesWithInitialId, maxIterations, 0);

		// apply the step logic: join with the edges, select the minimum neighbor, update if the
		// component of the candidate is smaller
		DataSet<Tuple2<Integer, Integer>> delta =
				iteration.getWorkset().join(edges, JoinOperatorBase.JoinHint.REPARTITION_HASH_FIRST)
						.where(0)
						.equalTo(0)
						.with(new NeighborWithComponentIDJoin())
						.groupBy(0)
						.aggregate(Aggregations.MIN, 1)
						.join(iteration.getSolutionSet())
						.where(0)
						.equalTo(0)
						.with(new ComponentIdFilter());

		// close the delta iteration (delta and new workset are identical)
		DataSet<Tuple2<Integer, Integer>> result = iteration.closeWith(delta, delta);

		// emit result
		result.writeAsCsv(outputPath, "\n", " ", FileSystem.WriteMode.OVERWRITE);

		// execute program
		try {
			JobExecutionResult jobResult = env.execute("Page Rank Iteration with Deltas");
			AccumulatorUtils.dumpAccumulators(jobResult);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// *************************************************************************
	//     USER FUNCTIONS
	// *************************************************************************

	@Override
	public String getDescription() {
		return "Parameters: <edges-path> <result-path> <max-number-of-iterations>";
	}
	
	// *************************************************************************
	//     UTIL METHODS
	// *************************************************************************
	
	private static String edgesPath = null;
	private static String outputPath = null;
	private static int maxIterations = 10;
	
	private static boolean parseParameters(String[] programArguments) {

		// parse input arguments
		if(programArguments.length == 3) {
			edgesPath = programArguments[0];
			outputPath = programArguments[1];
			maxIterations = Integer.parseInt(programArguments[2]);
		} else {
			System.err.println("Usage: ConnectedComponents <edges path> " +
					"<result path> <max number of iterations>");
			return false;
		}

		return true;
	}


}
