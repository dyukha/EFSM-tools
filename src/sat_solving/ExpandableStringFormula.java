package sat_solving;

/**
 * (c) Igor Buzhinsky
 */

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import bnf_formulae.BooleanFormula;
import bnf_formulae.BooleanFormula.DimacsConversionInfo;
import bnf_formulae.BooleanFormula.SolveAsSatResult;

public class ExpandableStringFormula {
	private final String initialFormula;
	private final Logger logger;
	private final SatSolver solver;
	private DimacsConversionInfo info;
	
	public DimacsConversionInfo info() {
		return info;
	}
	
	public ExpandableStringFormula(String initialFormula, Logger logger, SatSolver solver) {
		this.initialFormula = initialFormula;
		this.logger = logger;
		this.solver = solver;
	}
	
	/*
	 * Should be called after 'solve' was called at least once.
	 */
	public void addConstraints(List<String> constraints) throws IOException {
		if (info == null) {
			throw new AssertionError();
		}
		BooleanFormula.appendConstraintsToDimacs(logger, constraints, info);
	}
	
	public SolveAsSatResult solve(int timeLeftForSolver) throws IOException {
		if (info == null) {
			final SolveAsSatResult solution = BooleanFormula.solveAsSat(initialFormula,
					logger, timeLeftForSolver, solver);
			info = solution.info;
			return solution;
		}
		return BooleanFormula.solveDimacs(logger, timeLeftForSolver, solver, info);
	}
}
