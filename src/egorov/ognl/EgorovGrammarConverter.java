/**
 * GrammarConverter.java, 12.03.2008
 */
package egorov.ognl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import egorov.ltl.grammar.BinaryOperator;
import egorov.ltl.grammar.BinaryOperatorType;
import egorov.ltl.grammar.BooleanNode;
import egorov.ltl.grammar.LtlNode;
import egorov.ltl.grammar.UnaryOperator;
import egorov.ltl.grammar.UnaryOperatorType;
import egorov.ltl.grammar.exception.NotPredicateException;
import egorov.ltl.grammar.exception.UnexpectedMethodException;
import egorov.ltl.grammar.exception.UnexpectedOperatorException;
import egorov.ltl.grammar.exception.UnexpectedParameterException;
import egorov.ltl.grammar.predicate.IPredicateFactory;
import egorov.ltl.grammar.predicate.annotation.Predicate;
import egorov.statemachine.Action;
import egorov.statemachine.ControlledObject;
import egorov.statemachine.Event;
import egorov.statemachine.EventProvider;
import egorov.statemachine.SimpleState;
import egorov.statemachine.StateMachine;
import egorov.verifier.AutomataContext;
import rwth.i2.ltl2ba4j.model.IState;

/**
 * Convert from Ognl tree to LtlNode tree
 *
 * @author Kirill Egorov
 */
public class EgorovGrammarConverter {
    private final AutomataContext context;
    private final Object predicatesObj;
    private final Map<String, Method> predicates = new HashMap<>();

    public EgorovGrammarConverter(AutomataContext context, IPredicateFactory predicatesObj) {
        if (context == null) {
            throw new IllegalArgumentException("AutomataContext shouldn't be null");
        }
        if (predicatesObj == null) {
            throw new IllegalArgumentException("Predicates object shouldn't be null");
        }
        this.context = context;
        this.predicatesObj = predicatesObj;
        for (Method m: predicatesObj.getClass().getMethods()) {
            if (m.isAnnotationPresent(Predicate.class)) {
                if (!m.getReturnType().equals(boolean.class) && !m.getReturnType().equals(Boolean.class)) {
                    throw new NotPredicateException("Predicate method should return boolean type (" + m + ")");
                }
                predicates.put(m.getName(), m);
            }
        }
    }

    public LtlNode convert(Node root) {
        if (root == null) {
            throw new IllegalArgumentException("BuchiNode shouldn't be null");
        }
        if (root instanceof ASTMethod) {
            ASTMethod node = (ASTMethod) root;
            String name = node.getMethodName();

            //is unary operator?
            for (UnaryOperatorType type: UnaryOperatorType.values()) {
                if (type.getName().equalsIgnoreCase(name)) {
                    return createUnaryOperator(node, type);
                }
            }

            //is binary operator?
            for (BinaryOperatorType type: BinaryOperatorType.values()) {
                if (type.getName().equalsIgnoreCase(name)) {
                    return createBinaryOperator(node, type);
                }
            }

            //is predicate?
            Method predMethod = predicates.get(name);
            if (predMethod != null) {
                List<?> args = findArgs(node, predMethod);
                return new egorov.ltl.grammar.Predicate(predicatesObj, predMethod, args.toArray());
            }
            throw new UnexpectedMethodException(node.getMethodName());
        } else if (root instanceof ASTAnd) {
            return createBinaryOperator((SimpleNode) root, BinaryOperatorType.AND);
        } else if (root instanceof ASTOr) {
            return createBinaryOperator((SimpleNode) root, BinaryOperatorType.OR);
        } else if (root instanceof ASTNot) {
            return createUnaryOperator((SimpleNode) root, UnaryOperatorType.NEG);
        } else if (root instanceof ASTConst) {
            Object o = ((ASTConst) root).getValue();
            if (o instanceof Boolean) {
                return BooleanNode.getByName(o.toString());
            } else {
                throw new UnexpectedParameterException(o.getClass());
            }
        }
        throw new UnexpectedOperatorException(root.getClass().toString());
    }

    private List<?> findArgs(SimpleNode node, Method method) {
        Class<?>[] params = method.getParameterTypes();
        if ((node._children ==  null && params.length > 0)
                || (node._children !=  null && node._children.length != params.length)) {
            throw new UnexpectedMethodException("Unexpected method parameters count: " + method.getName());
        }
        List<Object> args = new ArrayList<Object>(params.length);
        for (int i = 0; i < params.length; i++) {
            Class<?> pClass = params[i];
            if (node._children[i] instanceof ASTConst) {
                Object o = ((ASTConst) node._children[i]).getValue();
                if (o == null) {
                    args.add(null);
                    continue;
                } else if (Number.class.isAssignableFrom(pClass)) {
                    if (o instanceof Number) {
                        args.add(o);
                        continue;
                    }
                } else if (Boolean.class.isAssignableFrom(pClass) || boolean.class.isAssignableFrom(pClass)) {
                    if (o instanceof Boolean) {
                        args.add(o);
                        continue;
                    }
                }
                throw new UnexpectedParameterException(pClass, o.toString());
            } else {
                if (StateMachine.class.isAssignableFrom(pClass)) {
                    String name = getValue((ASTProperty) node._children[i]);
                    addToList(args, context.getStateMachine(), pClass, name);
                } else if (ControlledObject.class.isAssignableFrom(pClass)) {
                    String name = getValue((ASTProperty) node._children[i]);
                    addToList(args, context.getControlledObject(), pClass, name);
                } else if (EventProvider.class.isAssignableFrom(pClass)) {
                    String name = getValue((ASTProperty) node._children[i]);
                    addToList(args, context.getEventProvider(), pClass, name);
                } else if (IState.class.isAssignableFrom(pClass)) {
                    ASTChain chain = (ASTChain) node._children[i];
                    StateMachine sm = context.getStateMachine();
                    String state = getValue((ASTProperty) chain._children[1]);
                    SimpleState s = sm.getState(state);
                    addToList(args, s, pClass, chain.toString());
                } else if (Event.class.isAssignableFrom(pClass)) {
                    ASTChain chain = (ASTChain) node._children[i];
                    EventProvider ep = context.getEventProvider();
                    String eventName = getValue((ASTProperty) chain._children[1]);
                    Event eventInst = ep.getEvent(eventName);
                    addToList(args, eventInst, pClass, chain.toString());
                } else if (Action.class.isAssignableFrom(pClass)) {
                    ASTChain chain = (ASTChain) node._children[i];
                    ControlledObject ctrlInst = context.getControlledObject();
                    String actionName = getValue((ASTProperty) chain._children[1]);
                    Action actionInst = ctrlInst.getAction(actionName);
                    addToList(args, actionInst, pClass, chain.toString());
                } else {
                    throw new UnexpectedParameterException(pClass);
                }
            }
        }
        return args;
    }

    private <E> void addToList(List<? super E> list, E e, Class<?> pClass, String param)
            throws UnexpectedParameterException {
        if (e == null) {
            throw new UnexpectedParameterException(pClass, param);
        }
        list.add(e);
    }

    private String getValue(ASTProperty prop) {
        ASTConst c = (ASTConst) prop._children[0];
        return (String) c.getValue();
    }

    private UnaryOperator createUnaryOperator(SimpleNode node, UnaryOperatorType type) {
        if (node._children.length != 1) {
            throw new UnexpectedOperatorException(node + " isn't unary operator");
        }
        UnaryOperator op = new UnaryOperator(type);
        op.setOperand(convert(node._children[0]));
        return op;
    }

    private BinaryOperator createBinaryOperator(SimpleNode node, BinaryOperatorType type) {
        if (node._children.length < 2) {
            throw new UnexpectedOperatorException(node + " isn't binary operation");
        }
        return createBinaryOperator(node, 0, type);
    }

    private BinaryOperator createBinaryOperator(SimpleNode node, int i, BinaryOperatorType type) {
        BinaryOperator op = new BinaryOperator(type);
        op.setLeftOperand(convert(node._children[i]));
        LtlNode right = (++i == node._children.length - 1) ? convert(node._children[i])
                                                        : createBinaryOperator(node, i, type);
        op.setRightOperand(right);
        return op;
    }
}