/**
 * IBuchiAutomata.java, 16.03.2008
 */
package qbf.egorov.ltl.buchi;

import java.util.Set;

/**
 * TODO: add comment
 *
 * @author Kirill Egorov
 */
public interface IBuchiAutomata {
    IBuchiNode getStartNode();
    Set<? extends IBuchiNode> getAcceptSet(int i);
    int getAcceptSetsCount();
    int size();
}