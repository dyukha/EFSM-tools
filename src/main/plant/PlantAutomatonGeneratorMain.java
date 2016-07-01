package main.plant;

/**
 * (c) Igor Buzhinsky
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import meta.Author;
import meta.MainBase;
import org.kohsuke.args4j.Option;

import scenario.StringActions;
import structures.plant.NondetMooreAutomaton;

public class PlantAutomatonGeneratorMain extends MainBase {
	@Option(name = "--size", aliases = { "-s" },
            usage = "size", metaVar = "<size>", required = true)
	private int size;

	@Option(name = "--eventNumber", aliases = { "-en" },
            usage = "used events number", metaVar = "<num>", required = true)
	private int eventNumber;

	@Option(name = "--actionNumber", aliases = { "-an" },
            usage = "used actions number", metaVar = "<num>", required = true)
	private int actionNumber;

	@Option(name = "--initialPercentage", aliases = { "-ip" },
            usage = "initial state percentage", metaVar = "<num>")
	private int initialPercentage = 25;

	@Option(name = "--minActions", aliases = { "-mina" },
            usage = "minumum number of actions in a state", metaVar = "<num>")
	private int minActions = 0;

	@Option(name = "--maxActions", aliases = { "-maxa" },
            usage = "maximum number of actions in a state", metaVar = "<num>")
	private int maxActions = 1;
	
	@Option(name = "--minTransitions", aliases = { "-mint" },
            usage = "minumum number of transitions from each state for each event", metaVar = "<num>")
	private int minTrans = 1;
	
	@Option(name = "--maxTransitions", aliases = { "-maxt" },
            usage = "maximum number transitions from each state for each event", metaVar = "<num>")
	private int maxTrans = 1;

	@Option(name = "--randseed", aliases = { "-rs" },
            usage = "random seed", metaVar = "<seed>")
	private int randseed;

	@Option(name = "--output", aliases = { "-o" },
            usage = "filepath to write the automaton in the GV format", metaVar = "<filepath>")
	private String filepath;

    public static void main(String[] args) {
        new PlantAutomatonGeneratorMain().run(args, Author.IB, "Random plant model generator");
    }

    @Override
    protected void launcher() throws IOException {
        initializeRandom(randseed);
        final List<Boolean> isStart = new ArrayList<>();
		final List<StringActions> stringActions = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			isStart.add(random().nextDouble() < initialPercentage * 0.01);
			final Set<String> thisActions = new LinkedHashSet<>();
			final int actionNum = minActions + random().nextInt(maxActions - minActions + 1);
			for (int j = 0; j < actionNum; j++) {
				String a;
				do {
					a = "z" + random().nextInt(actionNumber);
				} while (thisActions.contains(a));
				thisActions.add(a);
			}
			stringActions.add(new StringActions(String.join(", ", new ArrayList<>(thisActions))));
		}
		if (!isStart.contains(true)) {
			isStart.set(0, true);
		}

		final NondetMooreAutomaton automaton = new NondetMooreAutomaton(size, stringActions, isStart);
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < eventNumber; j++) {
				final String event = String.valueOf((char)('A' + j));
				final Set<Integer> present = new TreeSet<>();
				final int transitions = minTrans + random().nextInt(maxTrans - minTrans + 1);
				for (int k = 0; k < transitions; k++) {
					int dst;
					do {
						dst = random().nextInt(size);
					} while (present.contains(dst));
					automaton.state(i).addTransition(event, automaton.state(dst));
					present.add(dst);
				}
			}
		}
		
		if (filepath != null) {
            saveToFile(automaton, filepath);
		} else {
			System.out.println(automaton);
		}
	}
}
