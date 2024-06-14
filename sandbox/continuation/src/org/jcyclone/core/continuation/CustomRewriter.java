package org.jcyclone.core.continuation;

import org.apache.bcel.classfile.JavaClass;

/**
 * Interface for custom rewrite pass.
 */
public interface CustomRewriter {

	/**
	 * Perform rewriter pass of BCEL JavaClass object.
	 *
	 * @param jcl BCEL JavaClass object to rewrite
	 * @return rewritten/transformed BCEL JavaClass object
	 */
	JavaClass process(org.apache.bcel.classfile.JavaClass jcl) throws ClassNotFoundException;
}