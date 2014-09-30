package qbf.reduction;

/**
 * (c) Igor Buzhinsky
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BooleanVariable extends BooleanFormula implements Comparable<BooleanVariable> {
	public final String name;
	public final int number;
	
	private static int counter = 1;
	private static final List<BooleanVariable> ALL_VARS = new ArrayList<>();
	private static final Map<String, BooleanVariable> VARS_BY_NAME = new HashMap<>();
	
	private static String createName(String prefix, Object... indices) {
		assert indices.length == 0 || !prefix.contains("_");
		return indices.length == 0 ? prefix :
			Arrays.deepToString(indices).replace("[", prefix + "_").replace(", ", "_").replace("]", "");
	}

	private static int auxiliaryCounter = 1;
	
	public static BooleanVariable newAuxiliaryVariable() {
		return new BooleanVariable("h", auxiliaryCounter++);
	}
	
	public BooleanVariable(String prefix, Object... indices) {
		name = createName(prefix, indices);
		number = counter++;
		ALL_VARS.add(this);
		VARS_BY_NAME.put(name, this);
	}

	public static int variableCount() {
		return counter;
	}
	
	public static BooleanVariable getVarByNumber(int num) {
		return ALL_VARS.get(num - 1);
	}
	
	public static Optional<BooleanVariable> byName(String prefix, Object... indices) {
		return Optional.ofNullable(VARS_BY_NAME.get(createName(prefix, indices)));
	}
	
	@Override
	public String toString() {
		return name;
	}

	@Override
	public String toLimbooleString() {
		return String.valueOf(number);
	}

	@Override
	public int compareTo(BooleanVariable o) {
		return toString().compareTo(o.toString());
	}
}