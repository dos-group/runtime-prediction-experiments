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

import org.apache.flink.api.common.ProgramDescription;
import org.apache.flink.api.common.operators.base.JoinOperatorBase;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.aggregation.Aggregations;
import org.apache.flink.api.java.io.TypeSerializerInputFormat;
import org.apache.flink.api.java.io.TypeSerializerOutputFormat;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.core.fs.FileSystem;

/**
 * An implementation of the connected components algorithm, using a job per iteration.
 *
 * <p>
 * Input files are plain text files and must be formatted as follows:
 * <ul>
 * <li>Edges are represented as pairs for vertex IDs which are separated by tab
 * characters. Edges are separated by new-line characters.<br>
 * For example <code>"1	2\n2	12\n1	12\n42	63"</code> gives four (undirected) edges (1)-(2), (2)-(12), (1)-(12), and (42)-(63).
 * </ul>
 *
 * <p>
 * Usage: <code>ConnectedComponents &lt;vertices path&gt; &lt;edges path&gt; &lt;result path&gt; &lt;max number of iterations&gt;</code><br>
 * If no parameters are provided, the program is run with 10 iterations.
 */
@SuppressWarnings("serial")
public class ConnectedComponentsMultiJob extends AbstractConnectedComponents implements ProgramDescription {

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
		DataSet<Tuple2<Integer, Integer>> delta = verticesWithInitialId;
		DataSet<Tuple2<Integer, Integer>> newSolutionSet = verticesWithInitialId;

		//Run Connected Components for maxIterations
		for(int i = 0; i < maxIterations; i++){
			//read in files
			if(i > 0){
				//Read in parameters
				delta = env.readFile(new TypeSerializerInputFormat<>((delta.getType())),
						(intermediateResultsPath + "/iteration_delta_" + Integer.toString(i - 1)));
				newSolutionSet = env.readFile(new TypeSerializerInputFormat<>((newSolutionSet.getType())),
						(intermediateResultsPath + "/iteration_solution_" + Integer.toString(i - 1)));
			}

			// apply the step logic: join with the edges, select the minimum neighbor, update if the
			// component of the candidate is smaller
			delta = delta.join(edges, JoinOperatorBase.JoinHint.REPARTITION_HASH_FIRST)
					.where(0)
					.equalTo(0)
					.with(new NeighborWithComponentIDJoin())
					.groupBy(0)
					.aggregate(Aggregations.MIN, 1)
					.join(newSolutionSet)
					.where(0)
					.equalTo(0)
					.with(new ComponentIdFilter());

			newSolutionSet = newSolutionSet.coGroup(delta).where(0).equalTo(0).with(new UpdateSolutionSet());

			//Write out for next iteration
			if(i != maxIterations - 1) {
				delta.write(new TypeSerializerOutputFormat<>(),
						(intermediateResultsPath + "/iteration_delta_" + Integer.toString(i)), FileSystem.WriteMode.OVERWRITE);
				newSolutionSet.write(new TypeSerializerOutputFormat<>(),
						(intermediateResultsPath + "/iteration_solution_" + Integer.toString(i)), FileSystem.WriteMode.OVERWRITE);
				env.execute("Connected Components multi job");
			}
		}

		// emit result
		newSolutionSet.writeAsCsv(outputPath, "\n", " ", FileSystem.WriteMode.OVERWRITE);

		// execute program
		try {
			env.execute("Connected Components multi job");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// *************************************************************************
	//     USER FUNCTIONS
	// *************************************************************************

	@Override
	public String getDescription() {
		return "Parameters: <edges-path> <result-path> <max-number-of-iterations> <intermediate-result-path>";
	}

	// *************************************************************************
	//     UTIL METHODS
	// *************************************************************************

	private static String edgesPath = null;
	private static String outputPath = null;
	private static int maxIterations = 10;
	private static String intermediateResultsPath = null;

	private static boolean parseParameters(String[] programArguments) {

		// parse input arguments
		if(programArguments.length == 4) {
			edgesPath = programArguments[0];
			outputPath = programArguments[1];
			maxIterations = Integer.parseInt(programArguments[2]);
			intermediateResultsPath = programArguments[3];
		} else {
			System.err.println("Usage: ConnectedComponents <edges path> " +
					"<result path> <max number of iterations> <intermediate results path>");
			return false;
		}

		return true;
	}
}
