package algorithms;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import structures.Node;
import structures.ScenarioTree;
import structures.Transition;

public class AdjacencyCalculator {
    public static Map<Node, Set<Node>> getAdjacent(ScenarioTree tree) {
        Map<Node, Set<Node>> ans = new HashMap<>();
        calcNode(tree, tree.root(), ans);
        return ans;
    }
    
    private static void calcNode(ScenarioTree tree, Node node, Map<Node, Set<Node>> ans) {
        if (!ans.containsKey(node)) {
            Set<Node> adjacentSet = new LinkedHashSet<>();
            ans.put(node, adjacentSet);
            if (node.transitionCount() == 0) {
                return;
            }

            for (Transition t1 : node.transitions()) {
                // calculating for children
                calcNode(tree, t1.dst(), ans);
                for (Node other : tree.nodes()) {
                    if (other != node) {
                        for (Transition t2 : other.transitions()) {
                            if (t1.event().equals(t2.event())) {
                                if (t1.expr() == t2.expr()) {
                                    if (!t1.actions().equals(t2.actions())
                                            || ans.get(t1.dst()).contains(t2.dst())) {
                                        adjacentSet.add(other);
                                    }
                                } else if (t1.expr().hasSolutionWith(t2.expr())) {
                                    adjacentSet.add(other);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
