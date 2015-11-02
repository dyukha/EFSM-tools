package structures.plant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import scenario.StringActions;

public class MooreNode {
    private int number;
    private final StringActions actions;
    private final List<MooreTransition> transitions = new ArrayList<>();
    
    public MooreNode(int number, StringActions actions) {
        this.number = number;
        this.actions = actions;
    }
    
    public int number() {
        return number;
    }
    
    public StringActions actions() {
        return actions;
    }
    
    public boolean hasTransition(String event) {
    	return transitions.stream().anyMatch(t -> t.event().equals(event));
    }
    
    public void addTransition(String event, MooreNode dst) {
        final MooreTransition transition = new MooreTransition(this, dst, event);
        transitions.add(transition);
    }
    
    public Collection<MooreTransition> transitions() {
        return Collections.unmodifiableList(transitions);
    }
    
    public int transitionsCount() {
        return transitions.size();
    }
    
    @Override
    public String toString() {
    	return String.valueOf(number + " : " + actions);
    }
    
    public MooreNode getScenarioDst(String event, StringActions actions) {
    	for (MooreTransition t : transitions) {
    		if (t.event().equals(event) && t.dst().actions().equals(actions)) {
    			return t.dst();
    		}
    	}
    	return null;
    }
    
    public List<MooreNode> getAllDst(String event) {
    	final List<MooreNode> ans = new ArrayList<>();
    	for (MooreTransition t : transitions) {
    		if (t.event().equals(event)) {
    			ans.add(t.dst());
    		}
    	}
    	return ans;
    }
}