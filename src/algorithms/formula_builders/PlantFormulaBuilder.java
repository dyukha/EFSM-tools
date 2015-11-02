package algorithms.formula_builders;

/**
 * (c) Igor Buzhinsky
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import scenario.StringActions;
import structures.plant.MooreNode;
import structures.plant.MooreTransition;
import structures.plant.NegativePlantScenarioForest;
import structures.plant.NegativePlantScenarioForest.Loop;
import structures.plant.PositivePlantScenarioForest;
import bnf_formulae.BinaryOperation;
import bnf_formulae.BinaryOperations;
import bnf_formulae.BooleanFormula;
import bnf_formulae.BooleanVariable;
import bnf_formulae.FormulaList;

/**
 * (c) Igor Buzhinsky
 */

public class PlantFormulaBuilder {
	protected final int colorSize;
	protected final List<String> events;
	protected final List<String> actions;
	protected final PositivePlantScenarioForest positiveForest;
	protected final NegativePlantScenarioForest negativeForest;
	
	protected final List<BooleanVariable> vars = new ArrayList<>();
	
	public PlantFormulaBuilder(int colorSize, PositivePlantScenarioForest positiveForest,
			NegativePlantScenarioForest negativeForest, List<String> events, List<String> actions) {
		this.colorSize = colorSize;
		this.events = events;
		this.actions = actions;
		this.positiveForest = positiveForest;
		this.negativeForest = negativeForest;
	}
	
	public static BooleanVariable xVar(int node, int color) {
		return BooleanVariable.byName("x", node, color).get();
	}
	
	public static BooleanVariable yVar(int from, int to, String event) {
		return BooleanVariable.byName("y", from, to, event).get();
	}
	
	public static BooleanVariable zVar(int state, String action) {
		return BooleanVariable.byName("z", state, action).get();
	}
	
	public static BooleanVariable xxVar(int node, int color) {
		return BooleanVariable.byName("xx", node, color).get();
	}
	
	protected void addColorVars() {
		for (MooreNode node : positiveForest.nodes()) {
			for (int color = 0; color < colorSize; color++) {
				vars.add(new BooleanVariable("x", node.number(), color));
			}
		}
	}
	
	protected void addTransitionVars() {
		for (int nodeColor = 0; nodeColor < colorSize; nodeColor++) {
			for (String e : events) {
				for (int childColor = 0; childColor < colorSize; childColor++) {
					vars.add(new BooleanVariable("y", nodeColor, childColor, e));
				}
			}
			for (String action : actions) {
				vars.add(new BooleanVariable("z", nodeColor, action));
			}
		}
	}
	
	protected void addActionVars() {
		for (int nodeColor = 0; nodeColor < colorSize; nodeColor++) {
			for (String action : actions) {
				vars.add(new BooleanVariable("z", nodeColor, action));
			}
		}
	}
	
	protected void addNegativeColorVars() {
		for (MooreNode node : negativeForest.nodes()) {
			for (int color = 0; color < colorSize; color++) {
				vars.add(new BooleanVariable("xx", node.number(), color));
			}
		}
	}
	
	/*
	 * Each node has at least one color
	 */
	private BooleanFormula eachNodeHasColorConstraints() {
		final FormulaList constraints = new FormulaList(BinaryOperations.AND);
		for (MooreNode node : positiveForest.nodes()) {
			final FormulaList terms = new FormulaList(BinaryOperations.OR);
			for (int color = 0; color < colorSize; color++) {
				terms.add(xVar(node.number(), color));
			}
			constraints.add(terms.assemble());
		}
		return constraints.assemble("scenario constraints: each tree node has a color");
	}

	private BooleanFormula eachNodeHasOnlyColorConstraints() {
		final FormulaList constraints = new FormulaList(BinaryOperations.AND);
		for (MooreNode node : positiveForest.nodes()) {
			for (int color1 = 0; color1 < colorSize; color1++) {
				for (int color2 = 0; color2 < color1; color2++) {
					final BooleanVariable v1 = xVar(node.number(), color1);
					final BooleanVariable v2 = xVar(node.number(), color2);					
					constraints.add(v1.not().or(v2.not()));
				}
			}
		}
		return constraints.assemble("scenario constraints: each tree node has at most one color");
	}
	
	private BooleanFormula transitionConstraints() {
		final FormulaList constraints = new FormulaList(BinaryOperations.AND);
		for (MooreNode node : positiveForest.nodes()) {
			for (MooreTransition t : node.transitions()) {
				for (int nodeColor = 0; nodeColor < colorSize; nodeColor++) {
					for (int childColor = 0; childColor < colorSize; childColor++) {
						final BooleanVariable nodeVar = xVar(node.number(), nodeColor);
						final BooleanVariable childVar = xVar(t.dst().number(), childColor);
						final BooleanVariable relationVar = yVar(nodeColor, childColor, t.event());
						constraints.add(BinaryOperation.or(relationVar, nodeVar.not(), childVar.not()));
					}
				}
			}
		}
		return constraints.assemble("scenario constraints: connection between x's and y's");
	}
	
	private BooleanFormula actionEquality(int state, StringActions a) {
		final FormulaList constraints = new FormulaList(BinaryOperations.AND);
		final List<String> actionSequence = Arrays.asList(a.getActions());
		for (String action : actions) {
			final BooleanVariable var = zVar(state, action);
			constraints.add(actionSequence.contains(action) ? var : var.not());
		}
		return constraints.assemble();
	}
	
	private BooleanFormula scenarioActionConstraints() {
		final FormulaList constraints = new FormulaList(BinaryOperations.AND);
		for (MooreNode node : positiveForest.nodes()) {
			for (int nodeColor = 0; nodeColor < colorSize; nodeColor++) {
				constraints.add(xVar(node.number(), nodeColor).implies(actionEquality(nodeColor, node.actions())));
			}
		}
		return constraints.assemble("scenario action constraints");
	}
	
	private BooleanFormula eventCompletenessConstraints() {
		final FormulaList constraints = new FormulaList(BinaryOperations.AND);
		for (int i1 = 0; i1 < colorSize; i1++) {
			for (String e : events) {
				FormulaList options = new FormulaList(BinaryOperations.OR);
				for (int i2 = 0; i2 < colorSize; i2++) {
					options.add(yVar(i1, i2, e));
				}
				constraints.add(options.assemble());
			}
		}
		return constraints.assemble("induce a complete FSM");
	}
	
	private BooleanFormula negativeScenarioBasis() {
		final FormulaList constraints = new FormulaList(BinaryOperations.AND);
		for (MooreNode root : negativeForest.roots()) {
			for (int nodeColor = 0; nodeColor < colorSize; nodeColor++) {
				constraints.add(actionEquality(nodeColor, root.actions()).implies(xxVar(root.number(), nodeColor)));
			}
		}
		return constraints.assemble("negative scenario basis");
	}
	
	private BooleanFormula negativeScenarioPropagation() {
		final FormulaList constraints = new FormulaList(BinaryOperations.AND);
		for (MooreNode node : negativeForest.nodes()) {
			for (MooreTransition edge : node.transitions()) {
				final MooreNode childNode = edge.dst();
				final String event = edge.event();
				for (int color1 = 0; color1 < colorSize; color1++) {
					for (int color2 = 0; color2 < colorSize; color2++) {
						constraints.add(BinaryOperation.and(
								xxVar(node.number(), color1),
								yVar(color1, color2, event),
								actionEquality(color2, childNode.actions())).implies(
								xxVar(childNode.number(), color2)));
					}
				}
			}
		}
		return constraints.assemble("negative scenario propagation");
	}
	
	private BooleanFormula negativeScenarioTermination() {
		final FormulaList constraints = new FormulaList(BinaryOperations.AND);
		for (MooreNode node : negativeForest.terminalNodes()) {
			for (int nodeColor = 0; nodeColor < colorSize; nodeColor++) {
				constraints.add(xxVar(node.number(), nodeColor).not());
			}
		}
		return constraints.assemble("negative scenario termination");
	}
	
	private BooleanFormula negativeScenarioLoopPrevention() {
		final FormulaList constraints = new FormulaList(BinaryOperations.AND);
		for (Loop loop : negativeForest.loops()) {
			for (int color1 = 0; color1 < colorSize; color1++) {
				for (int color2 = 0; color2 < colorSize; color2++) {
					constraints.add(xxVar(loop.source.number(), color1)
							.and(xxVar(loop.destination.number(), color2))
							.implies(yVar(color1, color2, loop.event).not()));
				}
			}
		}
		return constraints.assemble("negative scenario loop prevention");
	}
	
	public FormulaList scenarioConstraints() {
		FormulaList constraints = new FormulaList(BinaryOperations.AND);
		// first node is always an initial state (probably there are more)
		addColorVars();
		addTransitionVars();
		addActionVars();
		constraints.add(xVar(0, 0));
		constraints.add(transitionConstraints());
		constraints.add(eventCompletenessConstraints());
		constraints.add(eachNodeHasColorConstraints());
		constraints.add(eachNodeHasOnlyColorConstraints());
		constraints.add(scenarioActionConstraints());
		
		addNegativeColorVars();
		constraints.add(negativeScenarioBasis());
		constraints.add(negativeScenarioPropagation());
		constraints.add(negativeScenarioTermination());
		constraints.add(negativeScenarioLoopPrevention());
		
		return constraints;
	}
}