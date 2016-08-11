package de.tuberlin.cit.experiments.prediction.flink.util;

import org.apache.flink.api.common.JobExecutionResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AccumulatorUtils {

	public static void dumpAccumulators(JobExecutionResult jobResult) {
		Map<String, Object> accumulators = jobResult.getAllAccumulatorResults();
		List<String> keys = new ArrayList<String>(accumulators.keySet());
		Collections.sort(keys);
		System.out.println("Accumulators:");
		for (String key : keys) {
			System.out.println(key + " : " + accumulators.get(key));
		}
	}

	public static void dumpAccumulators(JobExecutionResult result, int iteration) {
		Map<String, Object> accumulators = result.getAllAccumulatorResults();
		List<String> keys = new ArrayList<String>(accumulators.keySet());
		Collections.sort(keys);
		System.out.println("Accumulators:");
		for (String key : keys) {
			System.out.println(String.format(key, iteration) + " : " + accumulators.get(key));
		}
	}

}
