/**
 * ControlledObject.java, 02.03.2008
 */
package qbf.egorov.statemachine.impl;

import qbf.egorov.statemachine.IAction;
import qbf.egorov.statemachine.IControlledObject;
import qbf.egorov.statemachine.IFunction;

import java.util.*;
import java.lang.reflect.Method;

import com.evelopers.unimod.runtime.context.StateMachineContext;

/**
 * TODO: add comment
 *
 * @author Kirill Egorov
 */
public class ControlledObject implements IControlledObject {
    private String name;
    private Map<String, IAction> actions;
    private Map<String, IFunction> functions;

    public ControlledObject(String name, Class<?> implClass) {
        this.name = name;
        findMethods(implClass);
    }

    protected void findMethods(Class<?> clazz) {
        actions = new HashMap<>();
        functions = new HashMap<>();
        for (Method m: clazz.getMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 1 && StateMachineContext.class.isAssignableFrom(params[0])) {
                if (!m.getReturnType().equals(void.class)) {
                    IFunction func = new Function(m.getName(), m.getReturnType());
                    actions.put(func.getName(), func);
                    functions.put(func.getName(), func);
                } else {
                    actions.put(m.getName(), new Action(m.getName()));
                }
            }
        }
    }

    public String getName() {
        return name;
    }

    public IAction getAction(String actionName) {
        return actions.get(actionName);
    }

    public IFunction getFunction(String funName) {
        return functions.get(funName);
    }

    public Collection<IFunction> getFunctions() {
        return Collections.unmodifiableCollection(functions.values());
    }
}