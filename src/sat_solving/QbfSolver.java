package sat_solving;

/**
 * (c) Igor Buzhinsky
 */

public enum QbfSolver {
	DEPQBF("depqbf");
	
	public final String command;

	QbfSolver(String command) {
		this.command = command;
	}
}
