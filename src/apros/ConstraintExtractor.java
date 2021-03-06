package apros;

/**
 * (c) Igor Buzhinsky
 */

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConstraintExtractor {
	final static boolean OVERALL_1D = true;
	final static boolean OVERALL_2D = true;
	final static boolean INPUT_STATE = true;
	final static boolean CURRENT_NEXT = true;
	
	public static String plantCaption(Configuration conf) {
		final StringBuilder sb = new StringBuilder();
		final String inputLine = String.join(", ",
				conf.inputParameters.stream().map(p -> "CONT_INPUT_" + p.traceName())
    			.collect(Collectors.toList()));
		sb.append("MODULE PLANT(" + inputLine + ")\n");
    	sb.append("VAR\n");
    	for (Parameter p : conf.outputParameters) {
    		sb.append("    output_" + p.traceName() + ": 0.." + (p.valueCount() - 1) + ";\n");
    	}
    	return sb.toString();
	}
	
	public static String plantConversions(Configuration conf) {
		final StringBuilder sb = new StringBuilder();
		sb.append("DEFINE\n");
    	// output conversion to continuous values
    	for (Parameter p : conf.outputParameters) {
    		sb.append("    CONT_" + p.traceName() + " := case\n");
    		for (int i = 0; i < p.valueCount(); i++) {
    			sb.append("        output_" + p.traceName() + " = " + i + ": "
    					+ p.nusmvInterval(i) + ";\n");
    		}
    		sb.append("    esac;\n");
    	}
    	return sb.toString();
	}

	private static String interval(Collection<Integer> values, Parameter p, boolean next) {
        final String range = TraceModelGenerator.expressWithIntervals(values);
        if (p.valueCount() == 2 && range.equals("{0, 1}")) {
            return "TRUE";
        } else if (!range.contains("{") && !range.contains("union")) {
            final String[] tokens = range.split("\\.\\.");
            final int first = Integer.parseInt(tokens[0]);
            final int second = Integer.parseInt(tokens[1]);
            if (first == 0 && second == p.valueCount() - 1) {
                return "TRUE";
            }
        }
        return (next ? "next(" : "") + "output_" + p.traceName()
                + (next ? ")" : "") + " in " + range;
    }

	public static void run(Configuration conf, String datasetFilename) throws IOException {
		final Dataset ds = Dataset.load(datasetFilename);
		final StringBuilder sb = new StringBuilder();
		sb.append(plantCaption(conf));
        sb.append("    loop_executed: boolean;\n");
    	final List<String> initConstraints = new ArrayList<>();
    	final List<String> transConstraints = new ArrayList<>();

    	// 1. overall 1-dimensional constraints
    	if (OVERALL_1D) {
	    	for (Parameter p : conf.outputParameters) {
	    		final Set<Integer> indices = new TreeSet<>();
	    		for (List<double[]> trace : ds.values) {
	        		for (double[] snapshot : trace) {
						final int index = p.traceNameIndex(ds.get(snapshot, p));
						indices.add(index);
	        		}
	    		}
	    		initConstraints.add(interval(indices, p, false));
	    		transConstraints.add(interval(indices, p, true));
			}
    	}
    	// 2. overall 2-dimensional constraints
    	if (OVERALL_2D) {
	    	for (int i = 0; i < conf.outputParameters.size(); i++) {
				final Parameter pi = conf.outputParameters.get(i);
	    		for (int j = 0; j < i; j++) {
	    			final Parameter pj = conf.outputParameters.get(j);
		    		final Map<Integer, Set<Integer>> indexPairs = new TreeMap<>();
	        		for (List<double[]> trace : ds.values) {
	            		for (double[] snapshot : trace) {
	    					final int index1 = pi.traceNameIndex(ds.get(snapshot, pi));
	    					final int index2 = pj.traceNameIndex(ds.get(snapshot, pj));
	    					Set<Integer> secondIndices = indexPairs.get(index1);
							if (secondIndices == null) {
								secondIndices = new TreeSet<>();
								indexPairs.put(index1, secondIndices);
							}
							secondIndices.add(index2);
	            		}
	        		}
	        		for (List<String> list : Arrays.asList(initConstraints, transConstraints)) {
	        			final Function<Parameter, String> varName = p -> {
	        				String res = "output_" + p.traceName();
	        				if (list == transConstraints) {
	        					res = "next(" + res + ")";
	        				}
	        				return res;
	        			};
	        			
	        			final List<String> optionList = new ArrayList<>();
	    	    		for (Map.Entry<Integer, Set<Integer>> implication : indexPairs.entrySet()) {
	    	    			final int index1 = implication.getKey();
	    	    			optionList.add(varName.apply(pi) + " = " + index1 + " & "
                                    + interval(implication.getValue(), pj, false));
	    	    		}

	    	    		list.add(String.join(" | ", optionList));
	        		}
	        	}
	    	}
    	}

    	// 2. 2-dimensional constraints "input -> possible next state"
    	// if the input is unknown, then no constraint
    	
    	// FIXME do something with potential deadlocks, when unknown
    	// input combinations require non-intersecting actions
    	if (INPUT_STATE) {
    		for (Parameter pi : conf.inputParameters) {
	    		for (Parameter po : conf.outputParameters) {
		    		final Map<Integer, Set<Integer>> indexPairs = new TreeMap<>();
		    		for (int index1 = 0; index1 < pi.valueCount(); index1++) {
		    			indexPairs.put(index1, new TreeSet<>());
		    		}
		    		for (List<double[]> trace : ds.values) {
		        		for (int i = 0; i < trace.size() - 1; i++) {
		        			final int index1 = pi.traceNameIndex(ds.get(trace.get(i), pi));
							final int index2 = po.traceNameIndex(ds.get(trace.get(i + 1), po));
							indexPairs.get(index1).add(index2);
		        		}
		    		}
					final List<String> optionList = new ArrayList<>();
					for (int index1 = 0; index1 < pi.valueCount(); index1++) {
		    			optionList.add("CONT_INPUT_" + pi.traceName()
							+ " in " + pi.nusmvInterval(index1)
							+ (indexPairs.get(index1).isEmpty() ? ""
                                : (" & " + interval(indexPairs.get(index1), po, true))));
		    		}

					transConstraints.add(String.join(" | ", optionList));
	    		}
    		}
    	}
    	
    	// 2. 2-dimensional constraints "current state -> next state"
    	if (CURRENT_NEXT) {
    		for (Parameter p : conf.outputParameters) {
	    		final Map<Integer, Set<Integer>> indexPairs = new TreeMap<>();
	    		for (List<double[]> trace : ds.values) {
	        		for (int i = 0; i < trace.size() - 1; i++) {
	        			final int index1 = p.traceNameIndex(ds.get(trace.get(i), p));
						final int index2 = p.traceNameIndex(ds.get(trace.get(i + 1), p));
						Set<Integer> secondIndices = indexPairs.get(index1);
						if (secondIndices == null) {
							secondIndices = new TreeSet<>();
							indexPairs.put(index1, secondIndices);
						}
						secondIndices.add(index2);
	        		}
	    		}
				final List<String> optionList = new ArrayList<>();
	    		for (Map.Entry<Integer, Set<Integer>> implication : indexPairs.entrySet()) {
	    			final int index1 = implication.getKey();
	    			optionList.add("output_" + p.traceName()
						+ " = " + index1
						+ (" & " + interval(indexPairs.get(index1), p, true)));
	    		}
				transConstraints.add(String.join(" | ", optionList));
    		}
    	}
    	
    	sb.append("INIT\n");
        final int num = initConstraints.size() + transConstraints.size();
    	if (initConstraints.isEmpty()) {
        	initConstraints.add("TRUE");
    	}
    	sb.append("    (" + String.join(")\n  & (", initConstraints) + ")\n");
    	sb.append("TRANS\n");
    	if (transConstraints.isEmpty()) {
    		transConstraints.add("TRUE");
    	}
    	sb.append("    (" + String.join(")\n  & (", transConstraints) + ")\n");

        sb.append("ASSIGN\n");
        sb.append("    init(loop_executed) := FALSE;\n");
        sb.append("    next(loop_executed) := " + String.join(" & ",
                conf.outputParameters.stream()
                .map(p -> "output_" + p.traceName() + " = next(output_" + p.traceName() + ")")
                .collect(Collectors.toList())) + ";\n");
        sb.append("DEFINE\n");
        sb.append("    unsupported := FALSE;\n");
        sb.append(plantConversions(conf));

        final String outFilename = "plant-constraints.smv";
        try (PrintWriter pw = new PrintWriter(new File(outFilename))) {
            pw.println(sb);
        }

        System.out.println("Done; model has been written to: " + outFilename);
        System.out.println("Constraints generated: " + num);
	}
}
