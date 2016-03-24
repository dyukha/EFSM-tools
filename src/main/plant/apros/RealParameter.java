package main.plant.apros;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RealParameter extends Parameter {
	final List<Double> cutoffs;
	
	public RealParameter(String aprosName, String traceName, Double... cutoffs) {
		super(aprosName, traceName);
		this.cutoffs = new ArrayList<>(Arrays.asList(cutoffs));
		this.cutoffs.add(Double.POSITIVE_INFINITY);
	}
	
	@Override
	public List<String> traceNames() {
		final List<String> res = new ArrayList<>();
		for (int j = 0; j < cutoffs.size(); j++) {
			res.add(traceName(j));
		}
		return res;
	}

	@Override
	public List<String> descriptions() {
		final List<String> res = new ArrayList<>();
		for (int j = 0; j < cutoffs.size(); j++) {
			String s;
			if (cutoffs.size() == 1) {
				s = "any " + traceName();
			} else if (j == 0) {
				s = traceName() + " < " + cutoffs.get(j);
			} else if (j == cutoffs.size() - 1) {
				s = cutoffs.get(j - 1) + " ≤ " + traceName();
			} else {
				s = cutoffs.get(j - 1) + " ≤ " + traceName() + " < "
						+ cutoffs.get(j);
			}
			res.add("[" + j + "] " + s);
		}
		return res;
	}
	
	@Override
	public int traceNameIndex(double value) {
		for (int i = 0; i < cutoffs.size(); i++) {
			if (value < cutoffs.get(i)) {
				return i;
			}
		}
		throw new AssertionError();
	}

	@Override
	public int valueCount() {
		return cutoffs.size();
	}
	
	@Override
	public String toString() {
		return "param " + aprosName() + " (" + traceName() + "): REAL"
				+ cutoffs.subList(0, cutoffs.size() - 1);
	}

	@Override
	public String nusmvType() {
		return (Integer.MIN_VALUE + 1) + ".." + Integer.MAX_VALUE;
	}

	@Override
	public String nusmvCondition(String name, int index) {
		return name + " in " + (index == 0 ? (Integer.MIN_VALUE + 1)
				: (int) Math.round(Math.floor(cutoffs.get(index - 1))));
	}

    private int intervalMin(int interval) {
    	return interval == 0 ? (Integer.MIN_VALUE + 1)
				: (int) Math.round(Math.floor(cutoffs.get(interval - 1)));
    }
    
    private int intervalMax(int interval) {
    	return interval == cutoffs.size() - 1 ? Integer.MAX_VALUE
				: (int) Math.round(Math.ceil(cutoffs.get(interval)));
    }
    
	@Override
	public String nusmvInterval(int index) {
		return intervalMin(index) + ".." + intervalMax(index);
	}

	@Override
	public String defaultValue() {
		return "0";
	}
}
