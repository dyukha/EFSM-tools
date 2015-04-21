package algorithms;

/**
 * (c) Igor Buzhinsky
 */

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.apache.commons.lang3.tuple.Pair;

import qbf.egorov.ltl.grammar.LtlNode;
import qbf.reduction.BooleanFormula.SolveAsSatResult;
import qbf.reduction.BooleanVariable;
import qbf.reduction.ExpandableStringFormula;
import qbf.reduction.QbfSolver;
import qbf.reduction.QuantifiedBooleanFormula;
import qbf.reduction.QuantifiedBooleanFormula.FormulaSizeException;
import qbf.reduction.SatSolver;
import qbf.reduction.Verifier;
import structures.Automaton;
import structures.ScenariosTree;
import algorithms.AutomatonCompleter.CompletenessType;

public class HybridCounterexampleAutomatonBuilder extends ScenarioAndLtlAutomatonBuilder {
	private static final int MAX_FORMULA_SIZE = 500 * 1000 * 1000;
	
	public static Optional<Automaton> build(Logger logger, ScenariosTree tree,
			List<LtlNode> formulae, int size, String ltlFilePath,
			QbfSolver qbfSolver, String solverParams, boolean extractSubterms,
			List<String> events, List<String> actions, SatSolver satSolver,
			Verifier verifier, long finishTime, CompletenessType completenessType,
			int secToGenerateFormula, int secToSolve) throws IOException {		
		int k = -1;
		boolean maxKFound = false;
		int iteration = 1;
		ExpandableStringFormula formula = null;
		List<List<String>> counterexamples = null;
		Consumer<ExpandableStringFormula> closer = f -> {
			if (f != null) {
				f.close();
			}
		};
		final Set<String> forbiddenYs = QbfAutomatonBuilder.getForbiddenYs(logger, size, events.size());
		deleteTrash();
		SolveAsSatResult solution = null;
		Pair<Automaton, List<BooleanVariable>> autoSolution = null;
		while (System.currentTimeMillis() < finishTime) {
			if (maxKFound) {
				// deal with the previous FSM
				
				CounterexampleAutomatonBuilder.addCounterexamples(logger,
						counterexamples, autoSolution.getLeft(), formula, actions);
			} else {
				// try next k
				k++;
				final QuantifiedBooleanFormula qbf = new QbfFormulaBuilder(logger, tree,
						formulae, size, k, extractSubterms, completenessType,
						events, actions).getFormula(true);
				final long time = System.currentTimeMillis();
				try {
					@SuppressWarnings("resource")
					ExpandableStringFormula newFormula = new ExpandableStringFormula(qbf.flatten(tree, size, k,
							logger, events, actions, forbiddenYs, Math.min(finishTime,
							System.currentTimeMillis() + secToGenerateFormula * 1000), MAX_FORMULA_SIZE),
							logger, satSolver, solverParams);
					closer.accept(formula);
					formula = newFormula;
				} catch (FormulaSizeException | TimeLimitExceeded e) {
					logger.info("FORMULA FOR k = " + k +
							" IS TOO LARGE OR REQUIRES TOO MUCH TIME TO CONSTRUCT, STARTING ITERATIONS");
					logger.info("TRIED CREATING FORMULA FOR " + (System.currentTimeMillis() - time) + "ms");
					k--;
					maxKFound = true;
					continue;
				}
			}
			
			logger.info("TRYING k = " + k + (maxKFound ? (", iteration " + ++iteration) : ""));
			
			final int timeLeftForSolver = !maxKFound && k > 0
				? Math.min(timeLeftForSolver(finishTime), secToSolve)
				: timeLeftForSolver(finishTime);
			solution = formula.solve(timeLeftForSolver);
			final boolean unknown = solution.time >= timeLeftForSolver * 1000;
			
			if (solution.list().isEmpty()) {
				logger.info(unknown ? "UNKNOWN" : "UNSAT");
				closer.accept(formula);
				return Optional.empty();
			} else {
				autoSolution = constructAutomatonFromAssignment(logger, solution.list(), tree,
						size, true, completenessType);
				counterexamples = verifier.verifyWithCounterExamples(autoSolution.getLeft());
				final boolean verified = counterexamples.stream().allMatch(List::isEmpty);
				if (verified) {
					logger.info("SAT");
					closer.accept(formula);
					return Optional.of(autoSolution.getLeft());
				}
			}	
		}
		logger.info("TIME LIMIT EXCEEDED");
		closer.accept(formula);
		return Optional.empty();
	}
	
	private static int timeLeftForSolver(long finishTime) {
		return (int) (finishTime - System.currentTimeMillis()) / 1000 + 1;
	}
}