package structures;

/**
 * (c) Igor Buzhinsky
 */

import java.io.FileNotFoundException;
import java.text.ParseException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import sat_solving.Assignment;
import scenario.StringActions;
import scenario.StringScenario;
import bool.MyBooleanExpression;

public class NegativeScenariosTree {
    private final NegativeNode root;
    private final Set<NegativeNode> nodes;

    public NegativeScenariosTree() {
        this.root = new NegativeNode(0);
        this.nodes = new LinkedHashSet<>();
        this.nodes.add(root);
    }

    public NegativeNode getRoot() {
        return root;
    }

    public void checkColoring(List<Assignment> coloring, int colorSize) {
    	final Integer[] colors = new Integer[nodes.size()];
    	for (Assignment ass : coloring) {
    		if (!ass.value || !ass.var.name.startsWith("xx_")) {
    			continue;
    		}
    		final String[] tokens = ass.var.name.split("_");
    		int nodeIndex = Integer.parseInt(tokens[1]);
    		int nodeColor = Integer.parseInt(tokens[2]);
    		if (colors[nodeIndex] != null) {
    			throw new AssertionError("Duplicate color!");
    		}
    		colors[nodeIndex] = nodeColor;
    	}
    	for (Integer color : colors) {
    		if (color == null) {
    			throw new AssertionError("Node without a color!");
    		}
    	}
    	if (colors[root.number()] != 0) {
    		throw new AssertionError("Invalid color of the root!");
    	}
    	for (NegativeNode node : nodes) {
    		int nodeCol = colors[node.number()];
    		if (node.strongInvalid() && nodeCol != colorSize) {
    			throw new AssertionError("Invalid color of an invalid node!");
    		}
    		for (Node loop : node.loops()) {
    			if (nodeCol == colors[loop.number()] && nodeCol != colorSize) {
    				throw new AssertionError("Loop restriction is not satisfied!");
    			}
    		}
    	}
    }
    
    /*
     * varNumber = -1 for no variable removal
     */
    public void load(String filepath, int varNumber) throws FileNotFoundException, ParseException {
        for (StringScenario scenario : StringScenario.loadScenarios(filepath, varNumber)) {
            addScenario(scenario, 0);
        }
    }
    
    public void addScenario(StringScenario scenario, int loopLength) throws ParseException {
    	NegativeNode loopNode = null;
    	NegativeNode node = root;
        for (int i = 0; i < scenario.size(); i++) {
        	if (i == scenario.size() - loopLength) {
        		loopNode = node;
        	}
            addTransitions(node, scenario.getEvents(i), scenario.getExpr(i), scenario.getActions(i));
            node = node.dst(scenario.getEvents(i).get(0), scenario.getExpr(i), scenario.getActions(i));
        }
        if (loopLength == 0) {
        	loopNode = node;
        }
        if (loopNode == null) {
        	throw new AssertionError("loopNode is null!");
        }
        if (node.loops().contains(loopNode)) {
        	throw new AssertionError("Duplicate counterexample!");
        }
        node.addLoop(loopNode);
    }

    /*
     * If events.size() > 1, will add multiple edges towards the same destination.
     */
    private void addTransitions(NegativeNode src, List<String> events, MyBooleanExpression expr,
    		StringActions actions) throws ParseException {
    	assert !events.isEmpty();
    	NegativeNode dst = null;
    	for (String e : events) {
    		if (src.dst(e, expr, actions) == null) {
    			if (dst == null) {
            		dst = new NegativeNode(nodes.size());
            		nodes.add(dst);
            	}
                src.addTransition(e, expr, actions, dst);
    		}
    	}
    }

    public Collection<NegativeNode> nodes() {
        return nodes;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("# generated file, don't try to modify\n");
        sb.append("# command: dot -Tpng <filename> > tree.png\n");
        sb.append("digraph ScenariosTree {\n    node [shape = circle];\n");

        for (NegativeNode node : nodes) {
            for (Transition t : node.transitions()) {
                sb.append("    " + t.src().number() + " -> " + t.dst().number());
                sb.append(" [label = \"" + t.event() + " [" + t.expr().toString() + "] ("
                        + t.actions().toString() + ") \"];\n");
            }
            if (node.weakInvalid()) {
	            for (NegativeNode loop : node.loops()) {
	                sb.append("    " + node.number() + " -> " + loop.number() + ";\n");
	            }
            }
        }

        sb.append("}\n");
        return sb.toString();
    }
}
