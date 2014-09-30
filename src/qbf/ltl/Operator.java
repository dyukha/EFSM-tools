/**
 * Operator.java, 12.03.2008
 */
package qbf.ltl;

/**
 * TODO: add comment
 *
 * @author: Kirill Egorov
 */
public abstract class Operator<E> extends LtlNode {
    private E type;

    public Operator(E type) {
        super(type.toString());
        this.type = type;
    }

    public E getType() {
        return type;
    }
}