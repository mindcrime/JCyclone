package org.jcyclone.core.continuation;

public class Tag {

	/**
	 * Tags a method as call-with-continuation (blocking).
	 */
	public static class Blocking extends Error {
	}

	/**
	 * Tags a method (explicitly) as continuable.
	 */
	public static class Continuable extends Error {
	}

	/**
	 * Interface for custom rewrite pass.
	 */
//  public static interface CustomRewriter
//  {
//    /**
//     * Perform rewriter pass of BCEL JavaClass object.
//     *
//     * @param jcl BCEL JavaClass object to rewrite
//     * @return rewritten/transformed BCEL JavaClass object
//     */
//    org.apache.bcel.classfile.JavaClass process(org.apache.bcel.classfile.JavaClass jcl) throws ClassNotFoundException;
//  }

	/**
	 * Do not rewrite tagged class.
	 */
	public static interface DoNotRewrite {
	}
}
