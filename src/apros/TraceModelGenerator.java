package apros;

/**
 * (c) Igor Buzhinsky
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.tuple.Pair;

public class TraceModelGenerator {
	public static void run(Configuration conf, String datasetFilename) throws IOException {
		final Dataset ds = Dataset.load(datasetFilename);
		
		final int maxLength = ds.values.stream().mapToInt(v -> v.size()).max().getAsInt();
		final int minLength = ds.values.stream().mapToInt(v -> v.size()).min().getAsInt();
		if (maxLength != minLength) {
			throw new AssertionError("All traces are currently assumed to have equal lengths.");
		}

        final String outFilename = "trace-model.smv";
        final String individualTraceDir = "individual-trace-models";
        new File(individualTraceDir).mkdir();

        writeTraceModel(conf, ds, maxLength, 0, ds.values.size(), outFilename);
        for (int i = 0; i < ds.values.size(); i++) {
            writeTraceModel(conf, ds, maxLength, i, i + 1,
                    individualTraceDir + "/trace-model-" + i + ".smv");
        }

		System.out.println("Done; the model has been written to: " + outFilename);
        System.out.println("Individual trace models have been written to: " + individualTraceDir);
	}

    private static void writeTraceModel(Configuration conf, Dataset ds, int maxLength, int indexFrom, int indexTo,
                                        String filename) throws FileNotFoundException {
        final StringBuilder sb = new StringBuilder();
        sb.append(ConstraintExtractor.plantCaption(conf));
        sb.append("    step: 0.." + (maxLength - 1) + ";\n");
        sb.append("    unsupported: boolean;\n");
        sb.append("FROZENVAR\n    trace: " + indexFrom + ".." + (indexTo - 1) + ";\n");
        sb.append("ASSIGN\n");
        sb.append("    init(step) := 0;\n");
        sb.append("    next(step) := step < " + (maxLength - 1)
                + " ? step + 1 : " + (maxLength - 1) + ";\n");
        sb.append("    init(unsupported) := FALSE;\n");
        sb.append("    next(unsupported) := step = " + (maxLength - 1) + ";\n");

        for (Parameter p : conf.outputParameters) {
            sb.append("    output_" + p.traceName() + " := case\n");
            for (int traceIndex = indexFrom; traceIndex < indexTo; traceIndex++) {
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

                pairs.sort((v1, v2) -> Integer.compare(v2.getRight().size(), v1.getRight().size()));
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

        sb.append(ConstraintExtractor.plantConversions(conf));

        try (PrintWriter pw = new PrintWriter(filename)) {
            pw.println(sb);
        }
    }
	
	public static List<Pair<Integer, Integer>> intervals(Collection<Integer> values) {
		final List<Pair<Integer, Integer>> intervals = new ArrayList<>();
		int min = -1;
		int max = -1;
		for (int value : values) {
			if (min == -1) {
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
		return intervals;
	}
	
	public static String expressWithIntervals(Collection<Integer> values) {
		final List<Pair<Integer, Integer>> intervals = intervals(values);
		final List<String> stringIntervals = new ArrayList<>();
		final Set<Integer> separate = new TreeSet<>();
		for (Pair<Integer, Integer> interval : intervals) {
			if (interval.getLeft() + 1 >= interval.getRight()) {
				separate.add(interval.getLeft());
				separate.add(interval.getRight());
			} else {
				stringIntervals.add(interval.getLeft() + ".." + interval.getRight());
			}
		}
		if (!separate.isEmpty()) {
			stringIntervals.add(separate.toString().replace("[", "{").replace("]", "}"));
		}
		return String.join(" union ", stringIntervals);
	}
}
