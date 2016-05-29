package main.plant.apros;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

public class TraceModelGenerator {
	final static Configuration CONF = Settings.CONF;
	final static String FILENAME_PREFIX = "correct_recorded_";

	public static void main(String[] args) throws FileNotFoundException {		
		final Dataset ds = new Dataset(CONF.intervalSec,
				TraceTranslator.INPUT_DIRECTORY, FILENAME_PREFIX, TraceTranslator.PARAM_SCALES);
		
		final StringBuilder sb = new StringBuilder();
		final int maxLength = ds.values.stream().mapToInt(v -> v.size()).max().getAsInt();
		final int minLength = ds.values.stream().mapToInt(v -> v.size()).min().getAsInt();
		if (maxLength != minLength) {
			throw new AssertionError();
			// ASSUMES THAT EACH TRACE HAS EQUAL LENGTH
		}
		
		sb.append(ConstraintExtractor.plantCaption(CONF));
		sb.append("    step: 0.." + (maxLength - 1) + ";\n");
		sb.append("    unsupported: boolean;\n");
		sb.append("FROZENVAR\n    trace: 0.." + (ds.values.size() - 1) + ";\n");
		sb.append("ASSIGN\n");
		sb.append("    init(step) := 0;\n");
		sb.append("    next(step) := step < " + (maxLength - 1)
				+ " ? step + 1 : " + (maxLength - 1) + ";\n");
		sb.append("    init(unsupported) := FALSE;\n");
		sb.append("    next(unsupported) := step = " + (maxLength - 1) + ";\n");

		for (Parameter p : CONF.outputParameters) {
			sb.append("    output_" + p.traceName() + " := case\n");
			for (int traceIndex = 0; traceIndex < ds.values.size(); traceIndex++) {
				sb.append("        trace = " + traceIndex + ": case\n");
				final List<Set<Integer>> valuesToSteps = new ArrayList<>();
				for (int i = 0; i < p.valueCount(); i++) {
					valuesToSteps.add(new TreeSet<>());
				}
				for (int step = 0; step < ds.values.get(traceIndex).size(); step++) {
					final double value = ds.get(ds.values.get(traceIndex).get(step), p);
					final int res = p.traceNameIndex(value);
					valuesToSteps.get(res).add(step);
				}
				
				// more compact representation
				final List<Pair<Integer, Set<Integer>>> pairs = new ArrayList<>();
				for (int i = 0; i < p.valueCount(); i++) {
					if (!valuesToSteps.get(i).isEmpty()) {
						pairs.add(Pair.of(i, valuesToSteps.get(i)));
					}
				}
				
				pairs.sort((v1, v2) -> Integer.compare(v2.getRight().size(),
						v1.getRight().size()));
				pairs.add(pairs.remove(0)); // shift
				
				for (int i = 0; i < pairs.size(); i++) {
					final String condition = i == pairs.size() - 1 ? "TRUE"
							: ("step in " + expressWithIntervals(pairs.get(i).getRight()));
					sb.append("            " + condition + ": "
							+ pairs.get(i).getLeft() + ";\n");

				}
				sb.append("        esac;\n");
			}
			sb.append("    esac;\n");
		}
		
    	sb.append("DEFINE\n");
    	sb.append("    loop_executed := unsupported;\n");
		
		sb.append(ConstraintExtractor.plantConversions(CONF));
		
		try (PrintWriter pw = new PrintWriter("trace-model.smv")) {
			pw.println(sb);
		}
		System.out.println("Done.");
	}
	
	private static String expressWithIntervals(Set<Integer> values) {
		final List<Pair<Integer, Integer>> intervals = new ArrayList<>();
		Integer min = null;
		Integer max = null;
		for (int value : values) {
			if (min == null) {
				min = max = value;
			} else if (value == max + 1) {
				max = value;
			} else if (value <= max) {
				throw new AssertionError("Input set must contain increasing values.");
			} else {
				intervals.add(Pair.of(min, max));
				min = max = value;
			}
		}
		intervals.add(Pair.of(min, max));
		return String.join(" union ", intervals.stream().map(p -> p.getLeft() + ".." + p.getRight())
				.collect(Collectors.toList()));
	}
}
