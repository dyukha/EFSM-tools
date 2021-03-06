package apros;

/**
 * (c) Igor Buzhinsky
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

public class TraceTranslator {
	// to improve precision in the NuSMV model
    public static Map<String, Double> paramScales(String filename) {
        final Map<String, Double> paramScales = new TreeMap<>();
        try (Scanner sc = new Scanner(new File(filename))) {
            while (sc.hasNextLine()) {
                final String line = sc.nextLine().trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                final String[] tokens = line.split(" ");
                final String aprosName = tokens[0];
                final double scale = Double.parseDouble(tokens[1]);
                paramScales.put(aprosName, scale);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return paramScales;
    }

	private final static String OUTPUT_TRACE_FILENAME = "apros.sc";
	private final static String OUTPUT_ACTIONSPEC_FILENAME = "apros.actionspec";
	private final static String OUTPUT_LTL_FILENAME = "apros.ltl";
	
	private static void allEventCombinations(char[] arr, int index, Set<String> result, List<Parameter> parameters) {
		if (index == arr.length) {
			result.add(String.valueOf(arr));
		} else {
			final int intervalNum = parameters.get(index - 1).valueCount();
			for (int i = 0; i < intervalNum; i++) {
				arr[index] = Character.forDigit(i, 10);
				allEventCombinations(arr, index + 1, result, parameters);
			}
		}
	}
	
	public static List<String> generateScenarios(Configuration conf, Dataset ds, Set<List<String>> allActionCombinations,
			String gvOutput, String smvOutput, boolean addActionDescriptions, boolean satBased,
			boolean allEventCombinations, int traceIncludeEach)
            throws FileNotFoundException {
		// traces
		final Set<String> allEvents = new TreeSet<>();
		if (allEventCombinations) {
			// complete event set
			final char[] arr = new char[conf.inputParameters.size() + 1];
			arr[0] = 'A';
			allEventCombinations(arr, 1, allEvents, conf.inputParameters);
		}
		
		// coverage
		final Set<Pair<String, Integer>> inputCovered = new HashSet<>();
		final Set<Pair<String, Integer>> outputCovered = new HashSet<>();
		int totalInputValues = 0;
		int totalOutputValues = 0;
		for (Parameter p : conf.inputParameters) {
			totalInputValues += p.valueCount();
		}
		for (Parameter p : conf.outputParameters) {
			totalOutputValues += p.valueCount();
		}

        // for smoothness LTL properties
        final Map<Parameter, Integer> smoothnessLevels = new HashMap<>();
        for (Parameter p : conf.outputParameters) {
            smoothnessLevels.put(p, 1);
        }

        int addedTraces = 0;
		try (PrintWriter pw = new PrintWriter(new File(OUTPUT_TRACE_FILENAME))) {
            for (int i = 0; i < ds.values.size(); i++) {
                final List<double[]> trace = ds.values.get(i);
				final List<String> events = new ArrayList<>();
				final List<List<String>> actionCombinations = new ArrayList<>();

                for (int j = 0; j < trace.size(); j++) {
                    final double[] snapshot = trace.get(j);
					final StringBuilder event = new StringBuilder("A");
					final List<String> thisActions = new ArrayList<>();
					
					for (Parameter p : conf.inputParameters) {
						final double value = ds.get(snapshot, p);
						final int index = p.traceNameIndex(value);
						inputCovered.add(Pair.of(p.aprosName(), index));
						event.append(index);
					}
					
					for (Parameter p : conf.outputParameters) {
						final double value = ds.get(snapshot, p);
						final int index = p.traceNameIndex(value);
						outputCovered.add(Pair.of(p.aprosName(), index));
						thisActions.add(p.traceName(value));

                        if (satBased && j > 1) {
                            final int lastTraceIndex = p.traceNameIndex(ds.get(trace.get(j - 1), p));
                            final int smoothnessLevel = Math.abs(lastTraceIndex - index);
                            if (smoothnessLevel > smoothnessLevels.get(p)) {
                                smoothnessLevels.put(p, smoothnessLevel);
                            }
                        }
					}

					events.add(event.toString());
                    actionCombinations.add(thisActions);
				}

                boolean skip = i % traceIncludeEach != 0;
                if (!skip) {
                    allActionCombinations.addAll(actionCombinations);
                    allEvents.addAll(events);
                    final List<String> actions = actionCombinations.stream()
                            .map(l -> String.join(", ", l))
                            .collect(Collectors.toList());

                    events.add(0, "");
                    events.remove(events.size() - 1);
                    pw.println(String.join("; ", events));
                    pw.println(String.join("; ", actions));
                    addedTraces++;
                }
			}
		}

        System.out.println("Traces: " + addedTraces);

        if (satBased) {
            // actionspec
            try (PrintWriter pw = new PrintWriter(new File(
                    OUTPUT_ACTIONSPEC_FILENAME))) {
                for (Parameter p : conf.outputParameters) {
                    p.actionspec().forEach(pw::println);
                }
            }

            // smoothness temporal properties
            try (PrintWriter pw = new PrintWriter(new File(OUTPUT_LTL_FILENAME))) {
                for (Parameter p : conf.outputParameters) {
                    final int smoothnessLevel = smoothnessLevels.get(p);
                    System.out.println("smoothness(" + p.traceName() + ") = " + smoothnessLevel);
                    p.smoothnessTemporalProperties(smoothnessLevel).forEach(pw::println);
                }
            }
        }

		// all actions
		final List<String> allActions = conf.actions();
		final List<String> allActionDescriptions = conf.actionDescriptions();

		// execution command
		final int recommendedSize = allActionCombinations.size();
		final String nl = " \\\n";

		System.out.println("Run:");
		
		final List<String> builderArgs = new ArrayList<>();
		builderArgs.add(OUTPUT_TRACE_FILENAME);
		builderArgs.add("--actionNames");
		builderArgs.add(String.join(",", allActions));
		if (addActionDescriptions) {
			builderArgs.add("--actionDescriptions");
			builderArgs.add(String.join(",", allActionDescriptions));
		}
		builderArgs.add("--colorRules");
		builderArgs.add(String.join(",", conf.colorRules));
		builderArgs.add("--actionNumber");
		builderArgs.add(String.valueOf(allActions.size()));
		builderArgs.add("--eventNames");
		builderArgs.add(String.join(",", allEvents));
		builderArgs.add("--eventNumber");
		builderArgs.add(String.valueOf(allEvents.size()));
        if (satBased) {
            builderArgs.add("--ltl");
            builderArgs.add(OUTPUT_LTL_FILENAME);
        } else {
            builderArgs.add("--fast");
            System.out.println("# LTL disabled");
        }
        builderArgs.add("--actionspec");
		builderArgs.add(OUTPUT_ACTIONSPEC_FILENAME);
		builderArgs.add("--size");
		builderArgs.add(String.valueOf(recommendedSize));
		builderArgs.add("--varNumber");
		builderArgs.add("0");
		builderArgs.add("--result");
		builderArgs.add(gvOutput);
		builderArgs.add("--nusmv");
		builderArgs.add(smvOutput);

		System.out.print("java -jar jars/plant-automaton-generator.jar ");
		for (String arg : builderArgs) {
			if (arg.startsWith("--")) {
				System.out.print(nl + " " + arg + " ");
			} else {
				System.out.print("\"" + arg + "\"");
			}
		}
		System.out.println();

		// parameter limits
		System.out.println("Found parameter boundaries:");
		final Function<Parameter, String> describe = p -> p.traceName() + " in " + p.limits() + " - " + p;
		for (Parameter p : conf.outputParameters) {
			System.out.println(" output " + describe.apply(p));
		}
		for (Parameter p : conf.inputParameters) {
			System.out.println(" input " + describe.apply(p));
		}
		
		System.out.println(String.format("Input coverage: %.2f%%",
				100.0 * inputCovered.size() / totalInputValues));
		System.out.println(String.format("Output coverage: %.2f%%",
				100.0 * outputCovered.size() / totalOutputValues));
		
		return builderArgs;
	}
}
