package org.jcyclone.core.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;


/**
 * @author Graham Miller
 * @version $Id: ListInvocationHandler.java,v 1.1 2006/09/27 01:37:25 tolikuznets Exp $
 */
public class ListInvocationHandler implements InvocationHandler {
    private List referenceList;
    private String instanceID;

    public ListInvocationHandler(List aList, String anInstanceID)
    {
        referenceList = aList;
        instanceID = anInstanceID;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("Invoking "+method.getName()+" on "+instanceID);
        return method.invoke(referenceList,args); 
    }
}
