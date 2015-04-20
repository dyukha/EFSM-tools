package algorithms;

/**
 * (c) Igor Buzhinsky
 */

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import qbf.reduction.BinaryOperation;
import qbf.reduction.BinaryOperations;
import qbf.reduction.BooleanFormula;
import qbf.reduction.BooleanVariable;
import qbf.reduction.FalseFormula;
import qbf.reduction.FormulaList;
import structures.NegativeNode;
import structures.NegativeScenariosTree;
import structures.Node;
import structures.ScenariosTree;
import structures.Transition;
import algorithms.AutomatonCompleter.CompletenessType;

public class SatFormulaBuilderNegativeSC extends FormulaBuilder {
	private final NegativeScenariosTree negativeTree;
	
	public SatFormulaBuilderNegativeSC(ScenariosTree tree, int colorSize,
			List<String> events, List<String> actions,
			CompletenessType completenessType, NegativeScenariosTree negativeTree) {
		super(colorSize, tree, completenessType, events, actions);
		this.negativeTree = negativeTree;
	}
	
	public static BooleanVariable xxVar(int state, int color) {
		return BooleanVariable.byName("xx", state, color).get();
	}

	private void addNegativeScenarioVars() {
		for (Node node : negativeTree.getNodes()) {
			for (int color = 0; color <= colorSize; color++) {
				existVars.add(new BooleanVariable("xx", node.getNumber(), color));
			}
		}
		//System.out.println(existVars.stream().filter(v -> v.name.startsWith("xx_")).collect(Collectors.toList()));
	}
	
	private BooleanFormula eachNegativeNodeHasColorConstraints() {
		FormulaList constraints = new FormulaList(BinaryOperations.AND);
		for (NegativeNode node : negativeTree.getNodes()) {
			FormulaList terms = new FormulaList(BinaryOperations.OR);
			final int upperColor = node == negativeTree.getRoot() ? (colorSize - 1) : colorSize;
			for (int color = 0; color <= upperColor; color++) {
				terms.add(xxVar(node.getNumber(), color));
			}
			constraints.add(terms.assemble());
		}
		return constraints.assemble();
	}
	
	private BooleanFormula eachNegativeNodeHasOnlyColorConstraints() {
		FormulaList constraints = new FormulaList(BinaryOperations.AND);
		for (NegativeNode node : negativeTree.getNodes()) {
			for (int color1 = 0; color1 <= colorSize; color1++) {
				for (int color2 = 0; color2 < color1; color2++) {
					BooleanVariable v1 = xxVar(node.getNumber(), color1);
					BooleanVariable v2 = xxVar(node.getNumber(), color2);					
					constraints.add(v1.not().or(v2.not()));
				}
			}
		}
		return constraints.assemble();
	}
	
	private BooleanFormula invalidPropagationConstraints() {
		FormulaList constraints = new FormulaList(BinaryOperations.AND);
		for (NegativeNode node : negativeTree.getNodes()) {
			if (node == negativeTree.getRoot()) {
				continue;
			}
			for (Transition t : node.getTransitions()) {
				constraints.add(invalid(node).implies(invalid(t.getDst())));
			}
		}
		return constraints.assemble();
	}
	
	private BooleanFormula eachTerminalNodeIsInvalid() {
		FormulaList constraints = new FormulaList(BinaryOperations.AND);
		for (NegativeNode node : negativeTree.getNodes()) {
			if (node.terminal()) {
				constraints.add(invalid(node));
			}
		}
		return constraints.assemble();
	}
	
	private BooleanFormula properTransitionYConstraints() {
		FormulaList constraints = new FormulaList(BinaryOperations.AND);
		for (Node node : negativeTree.getNodes()) {
			for (Transition t : node.getTransitions()) {
				for (int nodeColor = 0; nodeColor < colorSize; nodeColor++) {
					for (int childColor = 0; childColor < colorSize; childColor++) {
						BooleanVariable nodeVar = xxVar(node.getNumber(), nodeColor);
						BooleanVariable childVar = xxVar(t.getDst().getNumber(), childColor);
						BooleanVariable relationVar = yVar(nodeColor, childColor, t.getEvent());
						constraints.add(BinaryOperation.or(relationVar, nodeVar.not(), childVar.not()));
						constraints.add(BinaryOperation.or(relationVar.not(), nodeVar.not(), childVar, invalid(t.getDst())));
						// !!! added invalid(child) recently
					}
				}
			}
		}
		return constraints.assemble();
	}
	
	private BooleanFormula properTransitionZConstraints() {
		FormulaList constraints = new FormulaList(BinaryOperations.AND);
		
		for (NegativeNode node : negativeTree.getNodes()) {
			FormulaList options = new FormulaList(BinaryOperations.OR);
			for (int i = 0; i < colorSize; i++) {
				FormulaList zConstraints = new FormulaList(BinaryOperations.AND);
				zConstraints.add(xxVar(node.getNumber(), i));
				for (Transition t : node.getTransitions()) {
					FormulaList innerConstraints = new FormulaList(BinaryOperations.AND);
					List<String> actionSequence = Arrays.asList(t.getActions().getActions());
					for (String action : actions) {
						BooleanFormula f = zVar(i, action, t.getEvent());
						if (!actionSequence.contains(action)) {
							f = f.not();
						}
						innerConstraints.add(f);
					}
					zConstraints.add(invalid(t.getDst()).or(innerConstraints.assemble()));
				}
				options.add(zConstraints.assemble());
			}
			constraints.add(invalid(node).or(options.assemble()));
		}
		
		return constraints.assemble();
	}
	
	private BooleanFormula invalidDefinitionConstraints() {
		FormulaList constraints = new FormulaList(BinaryOperations.AND);
		
		for (NegativeNode parent : negativeTree.getNodes()) {
			for (Transition t : parent.getTransitions()) {
				Node child = t.getDst();
				String event = t.getEvent();
				FormulaList options = new FormulaList(BinaryOperations.OR);
				options.add(invalid(parent));
				for (int colorParent = 0; colorParent < colorSize; colorParent++) {
					// either no transition
					FormulaList innerYConstraints = new FormulaList(BinaryOperations.AND);
					for (int colorChild = 0; colorChild < colorSize; colorChild++) {
						innerYConstraints.add(yVar(colorParent, colorChild, event).not());
					}
					BooleanFormula yFormula = completenessType == CompletenessType.NORMAL ?
							FalseFormula.INSTANCE : innerYConstraints.assemble();
					
					// or actions on the transition are invalid
					FormulaList innerZConstraints = new FormulaList(BinaryOperations.OR);
					for (String action : actions) {
						BooleanFormula v = zVar(colorParent, action, event);
						if (ArrayUtils.contains(t.getActions().getActions(), action)) {
							v = v.not();
						}
						innerZConstraints.add(v);
					}
					
					options.add(xxVar(parent.getNumber(), colorParent)
							.and(innerZConstraints.assemble().or(yFormula)));
				}
				constraints.add(invalid(child).equivalent(options.assemble()));
			}
		}
		
		return constraints.assemble();
	}
	
	private BooleanVariable invalid(Node n) {
		return xxVar(n.getNumber(), colorSize);
	}
	
	public BooleanFormula getFormula() {
		// actions should be included into the model!
		addColorVars();
		addTransitionVars(true);
		addNegativeScenarioVars();
		return scenarioConstraints(true).assemble()
				.and(xxVar(0, 0))
				.and(eachNegativeNodeHasColorConstraints())
				.and(eachNegativeNodeHasOnlyColorConstraints())
				.and(invalidPropagationConstraints())
				.and(eachTerminalNodeIsInvalid())
				.and(properTransitionYConstraints())
				.and(properTransitionZConstraints())
				.and(invalidDefinitionConstraints())
				.and(varPresenceConstraints());
	}
}
