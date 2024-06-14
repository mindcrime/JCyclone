package org.jcyclone.core.continuation;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.SyntheticRepository;
import org.jcyclone.core.continuation.ClassTraversal;
import org.jcyclone.core.continuation.CustomRewriter;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A custom class loader used by the JCyclone continuation-based scheduler to
 * modify event handlers at load-time.  Continuation provide total control over
 * thread scheduling and avoid context-switching overhead.
 * This implementation uses the Apache Byte-Code Engineering Library (BCEL).
 */
public class Rewriter extends ClassLoader {


	//////////////////////////////////////////////////
	// Constants
	//

    /**
     * Prefix for all classes to capture continuation state.
     */
    public static final String CONTINUATION_STATE_PREFIX = "_jcycloneCont";

    /**
     * Prefix of automatically generated fields.
     */
    public static final String JCYCLONE_FIELD_PREFIX = "_jcycloneField_";

    /**
     * Suffix of the field used to trigger static intialization.
     */
    public static final String STATIC_TRIGGER_SUFFIX = "_staticTrigger";

    /**
     * Name of the field used to trigger static initialization.
     */
    public static final String STATIC_TRIGGER = JCYCLONE_FIELD_PREFIX + STATIC_TRIGGER_SUFFIX;


    /**
	 * Logger instance.
	 */
	public static final Logger log = Logger.getLogger(Rewriter.class.getName());

	/**
	 * Class cache prefix.
	 */
	public static final String CACHE_PREFIX = "RewriterCache-";

	/**
	 * List of methods that are pre-defined to be blocking.
	 */
	public static java.lang.reflect.Member[] blockingMethod;

	static {
		try {
			blockingMethod = new java.lang.reflect.Method[]
			{
                            // write here methods which won't be auto-detected b/c their packages are ignored
			};
		} catch (Exception e) {
			throw new RewriterException("should not happen", e);
		}
	}

	/**
	 * List of methods that are pre-defined to be continuable.
	 */
	public static java.lang.reflect.Member[] continuableMethod;

	static {
		try {
			continuableMethod = new java.lang.reflect.Method[]
			{
                            // write here methods which won't be auto-detected b/c their packages are ignored
			};
		} catch (Exception e) {
			throw new RewriterException("show not happen", e);
		}
	}

	/**
	 * List of methods that are pre-defined to have every method blocking.
	 */
	public static final Class[] blockingClass =
	        {
	        };

	/**
	 * Class beginning with these strings are ignored, (used only when
	 * processedPackages is null).
	 */
	private static final String[] ignoredPackages = new String[]
	{
		"java.",
		"javax.",
		"sun.",
		"com.sun.",
		"org.apache.bcel."
	};

	/**
	 * Mutex access to BCEL repository object.
	 */
	private static final Object repositoryLock = new Object();

	//////////////////////////////////////////////////
	// state
	//

	/**
	 * Classes beginning with these strings are processed and all others are
	 * ignored; or, if this variable is null, then ignore packages in
	 * ignoredPackages and process all others.
	 */
	private String[] processedPackages;

	/**
	 * Rewrite cache. This is not just for performance...
	 * Must have for type-safety.
	 */
	private HashMap rewritten;

	/**
	 * Class disk-based cache directory.
	 */
	private String cacheDir;

	/**
	 * (Possibly remote) repository.
	 */
	private org.apache.bcel.util.Repository repository;

	/**
	 * Time that the rewriter class was compiled.
	 */
	private long rewriterTime;

	/**
	 * Methods should be rewritten to be Continuable.
	 */
	private HashSet continuable;

	/**
	 * Methods that are blocking.
	 */
	private HashSet blocking;

	/**
	 * Installed rewrite traversal visitors.
	 */
	private Vector rewriters;

	/**
	 * Classpath lookup cache: (string to JavaClass).
	 */
	private HashMap lookupCache;

	/**
	 * Regular method call graph: (methodsig mapped to hashset(methodsig)).
	 */
	private HashMap calledBy;

	//////////////////////////////////////////////////
	// initialization
	//


	/**
	 * Initialize a Rewriter instance, with a list of packages to
	 * be processed (others are ignored).
	 *
	 * @param processedPackages list of packages to process, or null to process
	 *                          all packages, except those in the (default) ignoredPackages list.
	 * @param cacheDir          directory for rewritten classfile cache
	 *                          //   * @param resources remote resources finder
	 * @param serverOut         server machine output stream
	 */
	public Rewriter(String[] processedPackages, String cacheDir,
	                PrintStream serverOut) {
		this.processedPackages = processedPackages;
		this.cacheDir = cacheDir;
        synchronized(repositoryLock) {
            this.repository = SyntheticRepository.getInstance();
        }
		this.lookupCache = new HashMap();
		this.calledBy = new HashMap();
		this.rewriterTime = Repository.lookupClassFile(Rewriter.class.getName()).getTime();
		this.rewritten = new HashMap();
		this.rewriters = new Vector();
		this.continuable = new HashSet();
		this.blocking = new HashSet();
		for (int i = 0; i < blockingClass.length; i++) {
			java.lang.reflect.Method[] methods = blockingClass[i].getDeclaredMethods();
			for (int j = 0; j < methods.length; j++) {
				blocking.add(getSignature(methods[j]).intern());
				log.finer("Found blocking method (blockingClass): " + getSignature(methods[j]));
			}
		}
		for (int i = 0; i < blockingMethod.length; i++) {
			blocking.add(getSignature(blockingMethod[i]).intern());
			log.finer("Found blocking method (blockingMethod): " + getSignature(blockingMethod[i]));
		}
		for (int i = 0; i < continuableMethod.length; i++) {
			addContinuable(getSignature(continuableMethod[i]));
		}
	}

	//////////////////////////////////////////////////
	// class loader functions
	//

	/**
	 * Find and load class. This method is invoked
	 * by the VM.
	 *
	 * @param name    qualified class name (e.g. java.lang.String)
	 * @param resolve not relevant
	 * @return requested class object
	 * @throws ClassNotFoundException thrown if class can not be found
	 * @throws VerifyError            throw if given class does not satify various
	 *                                programming constraints.
	 */
	public Class loadClass(String name, boolean resolve)
	        throws ClassNotFoundException, VerifyError {
		log.finer("** loading class " + name);
		// check for (and delegate) ignored packages
		return isIgnored(name)
		        ? super.loadClass(name, resolve)
		        : this.findClass(name);
	}

	/**
	 * Called by loadClass and actually does all the rewriting work,
	 * but checks in caches first.
	 *
	 * @param name qualified class name (e.g. java.lang.String)
	 * @return requested class object
	 * @throws ClassNotFoundException thrown if class can not be found
	 * @throws VerifyError            throw if given class does not satify various
	 *                                programming constraints.
	 */
	protected Class findClass(String name)
	        throws ClassNotFoundException, VerifyError {
		log.finer("** LOADING CLASS: " + name);
		// first look in the rewrite memory cache
		Class cl = (Class) rewritten.get(name);
		if (cl != null) return cl;

		// then check the disk-based cache
//    if(Main.REWRITE_CACHE && cacheDir!=null) cl = getDiskRewrittenClass(name);
		if (cl == null) {
			synchronized (repositoryLock) {
				// initialize bcel repository, just in case it is accessed internally during rewriting
				org.apache.bcel.util.Repository oldRepository = Repository.getRepository();

                Repository.setRepository(repository);

				// try to find requested class (unmodified)
				JavaClass jcl = lookupJavaClass(name);
				// collect statistics (pre-rewrite)
				int preTotalSize = 0, preConstSize = 0;
				if (log.isLoggable(Level.FINER)) {
					preTotalSize = jcl.getBytes().length;
					preConstSize = getConstantPoolSize(jcl);
				}

				// rewrite
				if (!isDoNotRewrite(jcl)) {

					jcl = rewriteClass(jcl);

					// emit statistics (post-rewrite)
					if (log.isLoggable(Level.FINER)) {
						// processed using script: bin/processClassSizeOuput.py
						String sizeinfo = "** REWRITING_SIZE_STATS: {";
						sizeinfo += "'name': '" + name + "', ";
						sizeinfo += "'preTotal': " + preTotalSize + ", ";
						sizeinfo += "'postTotal': " + jcl.getBytes().length + ", ";
						sizeinfo += "'preConst': " + preConstSize + ", ";
						sizeinfo += "'postConst': " + getConstantPoolSize(jcl) + ", ";
						sizeinfo += "'type': ";
						sizeinfo += 0;
						sizeinfo += ", ";
						sizeinfo += "}";
						log.finer(sizeinfo);
					}
				} else {
					// skip rewritting
					if (log.isLoggable(Level.INFO)) {
						log.info("** Rewriting class SKIPPED: " + name);
					}
				}
				// load it into the VM
				cl = define(jcl);
				Repository.setRepository(oldRepository);
			}
		} else {
			rewritten.put(name.intern(), cl);
		}
		return cl;
	}

	/**
	 * Search the class path and return requested BCEL JavaClass object. This is
	 * the ONLY point through which the Rewriter retrieves new JavaClass objects.
	 * Therefore, it is also a convenient point to perform caching and
	 * incremental maintenance of the call-graph.
	 *
	 * @param name classname
	 * @return BCEL JavaClass object
	 * @throws ClassNotFoundException when class not found
	 */
	public JavaClass lookupJavaClass(String name) throws ClassNotFoundException {
		// look in cache
		JavaClass jcl = (JavaClass) lookupCache.get(name);
		if (jcl != null) return jcl;

		// otherwise, look in repository (eventually disk)
		if (log.isLoggable(Level.FINER)) {
			log.finer("retrieving class from repository: " + name);
		}
		jcl = Repository.lookupClass(name);
		if (jcl == null) throw new ClassNotFoundException(name);

		// process installed rewriters
		if (!(isIgnored(name) || isDoNotRewrite(jcl))) {
			for (int i = rewriters.size() - 1; i >= 0; i--) {
				CustomRewriter cr = (CustomRewriter) rewriters.elementAt(i);
				jcl = cr.process(jcl);
			}
		}

		// put in cache
		// note: must come before call graph processing to ensure termination
		lookupCache.put(name.intern(), jcl);

		// process super
		JavaClass sup = jcl.getSuperClass();
		if (sup != null) lookupJavaClass(jcl.getClassName());

		// process callgraph
		if (!isIgnored(name)) {
			log.finer("** updating call graph after loading: " + name);
			updateCallGraphAndContinuable(jcl);
		}

		// return javaclass object
		return jcl;
	}

	/**
	 * Reset the JavaClass lookup cache.
	 */
	public void clearLookupCache() {
		Repository.clearCache();
		lookupCache = new HashMap();
	}

	/**
	 * Load a given BCEL class into the JVM.
	 *
	 * @param jcl BCEL Java class
	 * @return loaded class object
	 */
	public Class define(JavaClass jcl) {
		String name = jcl.getClassName();
		log.finer("** converting class structure back into bytecode for " + name);
		byte[] b = jcl.getBytes();
//    if(Main.REWRITE_CACHE && cacheDir!=null) putDiskRewrittenClass(name, b);
		Class cl = defineClass(name, b, 0, b.length);
		rewritten.put(name, cl);
		return cl;
	}

	/**
	 * Performs rewriting of given JavaClass file.
	 *
	 * @param jcl BCEL JavaClass structure to rewrite
	 * @return rewritten class object
	 * @throws VerifyError            throw if given class does not satify various
	 *                                programming constraints.
	 * @throws ClassNotFoundException failure to find referenced class
	 */
	protected JavaClass rewriteClass(JavaClass jcl) throws ClassNotFoundException, VerifyError {
		String name = jcl.getClassName();
		log.info("** Rewriting class: " + name);

		// update continuable method list
		computeContinuableFixedPoint();

		// verification
//    {
//      if(log.isDebugEnabled()) log.debug("** verifying class "+name);
//      RewriterTraversalVerifyAll va = new RewriterTraversalVerifyAll(this);
//      (new ClassTraversal(va)).processClass(jcl);
//      String[] errors = va.getErrors();
//      if(errors.length>0)
//      {
//        for(int i=0; i<errors.length; i++)
//        {
//          if(log.isDebugEnabled()) log.debug("verification error: "+errors[i]);
//        }
//        throw new VerifyError(errors[0]);
//      }
//    }

		// REWRITING

		// continuable method modifications
		log.finer("** modifying continuable methods: " + name);
//      jcl = (new ClassTraversal(new RewriterTraversalModifySynchronizedStatements(this))).processClass(jcl);
		jcl = (new ClassTraversal(new RewriterTraversalContinuableMethods(this))).processClass(jcl);

		return jcl;
	}


	//////////////////////////////////////////////////
	// disk rewriter class cache
	//

	/**
	 * Read a cached rewritten class file from disk.
	 *
	 * @param name class name
	 * @return rewritten class, or null if it does not exist/invalid
	 */
//  protected Class getDiskRewrittenClass(String name)
//  {
//    try
//    {
//      File f = new File(cacheDir, CACHE_PREFIX+name);
//      long cachetime = f.lastModified();
//      // ensure cached class is newer than original
//      long cltime = resources.getResourceLastModificationDate(classToFileName(name));
//      if(cltime>cachetime) return null;
//      // ensure cached class is newer than rewriter(s)
//      if(rewriterTime>cachetime) return null;
//      for(int i=0; i<rewriters.size(); i++)
//      {
//        long time = resources.getResourceLastModificationDate(
//            classToFileName(rewriters.elementAt(i).getClass().getName()));
//        if(time>cachetime) return null;
//      }
//      // return rewritten bits from disk
//      FileInputStream fin = new FileInputStream(f);
//      int size = (int)f.length(), read=0;
//      byte[] b = new byte[size];
//      while(read<size)
//      {
//        read += fin.read(b, read, size-read);
//      }
//      if(log.isDebugEnabled()) log.debug("** loading rewritten class from cache: "+name);
//      return defineClass(name, b, 0, b.length);
//    }
//    catch(Exception e)
//    {
//      if(log.isDebugEnabled()) log.debug("unable to read cache class file: "+e);
//      return null;
//    }
//  }

	/**
	 * Write a rewritten class file to the disk cache.
	 *
	 * @param name class name
	 * @param b    class bytecode
	 */
	protected void putDiskRewrittenClass(String name, byte[] b) {
		try {
			File f = new File(cacheDir, CACHE_PREFIX + name);
			FileOutputStream fos = new FileOutputStream(f);
			fos.write(b);
			fos.close();
		} catch (Exception e) {
			log.info("unable to cache rewritten class file: " + e);
		}
	}

	//////////////////////////////////////////////////
	// class analysis functions
	//

	/**
	 * Add signature to list of continables. Verify that it is not a constructor,
	 * and return whether it is newly discovered.
	 *
	 * @param sig continuable to add
	 * @return whether this is a newly found continuable method
	 * @throws VerifyError if signature is of an initialization method
	 */
	public boolean addContinuable(String sig) throws VerifyError {
		boolean added = continuable.add(sig.intern());
		if (added) {
			log.finer("Found continuable method: " + sig);
		}
		if (added && sig.indexOf(Constants.CONSTRUCTOR_NAME) != -1) {
			throw new VerifyError("continuable constructor detected: " + sig);
		}
		return added;
	}

	/**
	 * Update call-graph with methods of a new class.
	 *
	 * @param jcl BCEL class object to analyze
	 * @throws ClassNotFoundException failure to load class in call graph
	 */
	public void updateCallGraphAndContinuable(JavaClass jcl) throws ClassNotFoundException {
		ConstantPoolGen cpg = new ConstantPoolGen(jcl.getConstantPool());
		Method[] methods = jcl.getMethods();
		// loop through all methods looking for blocking
		for (int i = 0; i < methods.length; i++) {
			MethodGen mg = new MethodGen(methods[i], jcl.getClassName(), cpg);
			if (isBlocking(mg)) {
				boolean added = blocking.add(getSignature(mg).intern());
				if (added) {
					log.finer("Found blocking method: " + getSignature(mg));
				}
			}
		}
		// loop through all methods looking for continuables
		for (int i = 0; i < methods.length; i++) {
			MethodGen mg = new MethodGen(methods[i], jcl.getClassName(), cpg);
			// check for explicit continuables
			if (isContinuable(mg)) {
				addContinuable(getSignature(mg));
			}
			String mySig = getSignature(mg);
			// loop through every instruction
			InstructionList il = mg.getInstructionList();
			if (il == null) continue;
			Instruction[] instructions = il.getInstructions();
			for (int j = 0; j < instructions.length; j++) {
				Instruction inst = instructions[j];
				// find invocation instructions
				if (inst instanceof InvokeInstruction) {
					InvokeInstruction ii = (InvokeInstruction) inst;
					// first we recurse. terminating b/c of lookupCache
					lookupJavaClass(ii.getClassName(cpg));
					// now, classify the call are entity call or regular method call
//          if(ii instanceof INVOKEVIRTUAL && isEntity(ii.getClassName(cpg))
//              || ii instanceof INVOKEINTERFACE)
//          {
//            // regular or proxy entity call
//            if(isBlocking(ii.getClassName(cpg), ii.getMethodName(cpg),
//                  ii.getReturnType(cpg), ii.getArgumentTypes(cpg)))
//            {
//              // entity call to blocking method
//              addContinuable(getSignature(mg));
//            }
//          }
//          else
					{
						// add to call-graph
						String isig = getSignature(ii, cpg);
						HashSet callers = (HashSet) calledBy.get(isig);
						if (callers == null) {
							callers = new HashSet();
							calledBy.put(isig.intern(), callers);
						}
						callers.add(mySig.intern());
					}
				} // if instanceof InvokeInstruction
			} // foreach instruction
		} // foreach method
	} // function: updateCallGraphAndContinuable

	/**
	 * Compute continuable fixed-point using call-graph.
	 */
	public void computeContinuableFixedPoint() {
		boolean changed = true;
		while (changed) {
			changed = false;
			Iterator it = ((HashSet) continuable.clone()).iterator();
			while (it.hasNext()) {
				HashSet callers = (HashSet) calledBy.get(it.next());
				if (callers == null) continue;
				Iterator it2 = callers.iterator();
				while (it2.hasNext()) {
					changed |= addContinuable((String) it2.next());
				}
			}
		}
	} // function: performContinuableFixedPoint

	/**
	 * Return all the classes that are statically, directly referenced by this
	 * classes.
	 *
	 * @param classname name of class to inspect
	 * @return array of classes statically referenced directly
	 * @throws ClassNotFoundException failure to find referenced class
	 */
	public String[] getAllClassReferences(String classname)
	        throws ClassNotFoundException {
		JavaClass jcl = lookupJavaClass(classname);
		ConstantPool cp = jcl.getConstantPool();
		Constant[] entries = cp.getConstantPool();
		Vector result = new Vector();
		for (int i = 0; i < entries.length; i++) {
			if (entries[i] instanceof ConstantClass) {
				result.add(((ConstantClass) entries[i]).getBytes(cp).replace('/', '.'));
			}
		}
		String[] result2 = new String[result.size()];
		result.copyInto(result2);
		return result2;
	}

	/**
	 * Return all the classes that are statically, recursively (directly and
	 * indirectly) referenced by this class.
	 *
	 * @param classname name of class to inspect
	 * @return array of classes statically referenced directly or indirectly
	 * @throws ClassNotFoundException failure to find referenced class
	 */
	public String[] getAllClassReferencesRecursively(String classname) throws ClassNotFoundException {
		HashSet list = new HashSet();
		Stack toProcess = new Stack();
		toProcess.add(classname);
		String[] stringArray = new String[0];
		while (!toProcess.isEmpty()) {
			String s = (String) toProcess.pop();
			String[] refs = getAllClassReferences(s);
			list.add(s);
			for (int i = 0; i < refs.length; i++) {
				if (list.contains(refs[i])) continue;
				if (isIgnored(refs[i])) continue;
				toProcess.push(refs[i]);
			}
		}
		return (String[]) list.toArray(stringArray);
	}

	/**
	 * Return an array of the names of all interfaces implemented by a given class.
	 *
	 * @param jcl BCEL JavaClass object to inspect
	 * @return array of the names of all interfaces implemented.
	 * @throws ClassNotFoundException interface classes not found
	 */
	public String[] getInterfaceNames(JavaClass jcl) throws ClassNotFoundException {
		String[] interfaces = jcl.getInterfaceNames();
		// collect all direct interfaces
		String supercl = jcl.getSuperclassName();
		while (supercl != null && !supercl.equals(Object.class.getName())) {
			jcl = lookupJavaClass(supercl);
			interfaces = Util.union(interfaces, jcl.getInterfaceNames());
			supercl = jcl.getSuperclassName();
		}
		// expand to find all indirect interfaces
		String[] interfaces2 = interfaces;
		do {
			interfaces = interfaces2;
			for (int i = 0; i < interfaces.length; i++) {
				interfaces2 = Util.union(interfaces2,
				        lookupJavaClass(interfaces[i]).getInterfaceNames());
			}
		} while (interfaces2.length != interfaces.length);
		interfaces = interfaces2;
		return interfaces;
	}

	/**
	 * Find class that declares a method, or null otherwise.
	 *
	 * @param classname    classname to initiate search from
	 * @param methodname   name of method being sought
	 * @param methodreturn BCEL return type of method being sought
	 * @param methodargs   BCEL argument type of method being sought
	 * @return name of class that declares given method
	 * @throws ClassNotFoundException parent class not found
	 */
	public String getDeclaringClass(String classname, String methodname, Type methodreturn, Type[] methodargs) throws ClassNotFoundException {
		if (classname == null) return null;
		// check locally
		JavaClass jcl = lookupJavaClass(classname);
		if (containsMethod(jcl, methodname, methodreturn, methodargs)) {
			return jcl.getClassName();
		}
		// recurse to parent
		String sup = jcl.getSuperclassName();
		if (!classname.equals(sup)) {
			String result = getDeclaringClass(jcl.getSuperclassName(), methodname, methodreturn, methodargs);
			if (result != null) return result;
		}
		// recurse through interfaces
		String[] interf = jcl.getInterfaceNames();
		for (int i = 0; i < interf.length; i++) {
			String result = getDeclaringClass(interf[i], methodname, methodreturn, methodargs);
			if (result != null) return result;
		}
		return null;
	}

	/**
	 * Return whether method contained (declared) within class.
	 *
	 * @param jcl          BCEL JavaClass object to inspect
	 * @param methodname   method name
	 * @param methodreturn method return BCEL type
	 * @param methodargs   method parameter BCEL types
	 * @return whether method is contained within class
	 */
	public static boolean containsMethod(JavaClass jcl, String methodname, Type methodreturn, Type[] methodargs) {
		String sig = Type.getMethodSignature(methodreturn, methodargs);
		Method[] methods = jcl.getMethods();
		for (int i = 0; i < methods.length; i++) {
			if (!methodname.equals(methods[i].getName())) continue;
			if (!sig.equals(methods[i].getSignature())) continue;
			return true;
		}
		return false;
	}

	//////////////////////////////////////////////////
	// static rewriter helper functions
	//

	/**
	 * Determine whether a class should be rewritten or
	 * left alone. If processedPackages is defined, then we
	 * only process packages with those prefixes. Otherwise,
	 * any package that does not begin with a prefix in the
	 * <code>ignoredPackages</code> array is rewritten.
	 *
	 * @param classname given class name
	 * @return whether the given class should be rewritten
	 */
	public boolean isIgnored(String classname) {
		if (processedPackages != null) {
			for (int i = 0; i < processedPackages.length; i++) {
				if (processedPackages[i].length() == 0) {
					// default package
					// note: does not deal with inner classes in default package
					if (classname.indexOf('.') == -1) {
						return false;
					}
				} else {
					// regular package
					if (classname.startsWith(processedPackages[i])) {
						return false;
					}
				}
			}
			return true;
		} else {
			return isIgnoredStatic(classname);
		}
	}

	/**
	 * Determine whether a class should be rewritten or
	 * left alone. Any package that does not begin with
	 * a prefix in the <code>ignoredPackages</code> array is
	 * rewritten.
	 *
	 * @param classname given class name
	 * @return whether the given class should be rewritten
	 */
	public static boolean isIgnoredStatic(String classname) {
		for (int i = 0; i < ignoredPackages.length; i++) {
			if (classname.indexOf('.') != -1 && classname.startsWith(ignoredPackages[i])) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a given method has blocking semantics (whether the caller needs to
	 * call with continuation). A method is blocking iff it declares that it
	 * throws a {@link Tag.Blocking}.
	 *
	 * @param mg method object
	 * @return whether method is blocking
	 */
	public static boolean isBlocking(MethodGen mg) {
		return Util.contains(mg.getExceptions(), Tag.Blocking.class.getName());
	}

	/**
	 * Whether a specified method has blocking semantics (whether the caller
	 * needs to call with continuation). A method is blocking iff it declares
	 * that it throws a {@link Tag.Blocking}.
	 *
	 * @param classname    class name
	 * @param methodname   method name
	 * @param methodreturn method return type
	 * @param methodargs   method parameter types
	 * @return whether method is blocking
	 * @throws ClassNotFoundException could not find parent classes
	 */
	public boolean isBlocking(String classname, String methodname, Type methodreturn, Type[] methodargs) throws ClassNotFoundException {
		classname = getDeclaringClass(classname, methodname, methodreturn, methodargs);
		if (classname == null) return false;
		return blocking.contains(getSignature(classname, methodname, methodreturn, methodargs));
	}

	/**
	 * Whether a given method is marked as "continuable". Continuable methods are
	 * methods that calls blocking methods (those that declare throws
	 * Continuation) or other continuable methods in the same entity.
	 *
	 * @param mg BCEL MethodGen object
	 * @return whether method is "continuable"
	 */
	public boolean isContinuable(MethodGen mg) {
		return Util.contains(mg.getExceptions(), Tag.Continuable.class.getName())
		        || continuable.contains(getSignature(mg));
	}

	/**
	 * Whether a given invocation target is "continuable". Continuable methods
	 * are methods that calls blocking methods (those that declare throws
	 * Continuation) or other continuable methods in the same entity.
	 *
	 * @param ii  invocation instruction
	 * @param cpg class constant pool
	 * @return whether invocation target is "continuable"
	 */
	public boolean isContinuable(InvokeInstruction ii, ConstantPoolGen cpg) {
		return continuable.contains(getSignature(ii, cpg));
	}

	/**
	 * Determine whether given class should not be rewritten. A class can turn
	 * off the rewriter by implementing the <code>Tag.DoNotRewrite</code>
	 * interface.
	 * <p/>
	 *
	 * @param c BCEL class object
	 * @return whether given class has rewriting disabled
     * @see Tag.DoNotRewrite
	 */
	public boolean isDoNotRewrite(JavaClass c) throws ClassNotFoundException {
		if (c == null) return false;
		return Util.contains(getInterfaceNames(c), Tag.DoNotRewrite.class.getName());
	}

	/**
	 * Modify the name of a class.
	 *
	 * @param jcl  BCEL class object
	 * @param name new name for class
	 */
	public static void setClassName(JavaClass jcl, String name) {
		jcl.setClassName(name);
		ConstantPool cp = jcl.getConstantPool();
		ConstantClass cc = (ConstantClass) cp.getConstant(jcl.getClassNameIndex(), Constants.CONSTANT_Class);
		ConstantUtf8 nm = (ConstantUtf8) cp.getConstant(cc.getNameIndex(), Constants.CONSTANT_Utf8);
		nm.setBytes(name.replace('.', '/'));
	}

	/**
	 * Emit the access flags to Rewriter log.
	 *
	 * @param a access flags to log
	 */
	public static void logAccessFlags(AccessFlags a) {
		if (log.isLoggable(Level.FINER)) {
			String s = "access flags: ";
			if (a.isAbstract()) s += ("abstract ");
			if (a.isFinal()) s += ("final ");
			if (a.isInterface()) s += ("interface ");
			if (a.isNative()) s += ("native ");
			if (a.isPrivate()) s += ("private ");
			if (a.isProtected()) s += ("protected ");
			if (a.isPublic()) s += ("public ");
			if (a.isStatic()) s += ("static ");
			if (a.isStrictfp()) s += ("strictfp ");
			if (a.isSynchronized()) s += ("synchronized ");
			if (a.isTransient()) s += ("transient ");
			if (a.isVolatile()) s += ("volatile ");
			log.finer(s);
		}
	}

	/**
	 * Return the BCEL Type object for a corresponding Java
	 * Class object.
	 *
	 * @param c type given as Java class object
	 * @return given type as a BCEL Type object
	 */
	public static Type getType(Class c) {
		return Type.getType(getSignature(c));
	}

	/**
	 * Return a BCEL Type object array corresponding to Java
	 * Class object array.
	 *
	 * @param c type array given as Java class objects
	 * @return given type array converted to BCEL Type objects
	 */
	public static Type[] getTypes(Class[] c) {
		Type[] t = new Type[c.length];
		for (int i = 0; i < t.length; i++) {
			t[i] = Rewriter.getType(c[i]);
		}
		return t;
	}

	/**
	 * Return a Class object for a given primitive Java Type.
	 *
	 * @param t primitive type given as BCEL Type object
	 * @return primitive type as Java class object
	 */
	public static Class getPrimitiveObjectType(BasicType t) {
		char c = t.getSignature().charAt(0);
		switch (c) {
			case 'Z':
				return Boolean.class;
			case 'C':
				return Character.class;
			case 'B':
				return Byte.class;
			case 'S':
				return Short.class;
			case 'I':
				return Integer.class;
			case 'J':
				return Long.class;
			case 'F':
				return Float.class;
			case 'D':
				return Double.class;
			default:
				throw new RuntimeException("unknown primitive type: " + t);
		}
	}

	/**
	 * Return name of method used to convert a primitive type wrapper object to a
	 * primitive. (eg. doubleValue to convert Double to double.)
	 *
	 * @param t primitive BCEL type object
	 * @return name of method used to convert a primitive type wrapper object to
	 *         a primitive type.
	 */
	public static String getPrimitiveObjectConversionMethod(BasicType t) {
		char c = t.getSignature().charAt(0);
		switch (c) {
			case 'Z':
				return "booleanValue";
			case 'C':
				return "charValue";
			case 'B':
				return "byteValue";
			case 'S':
				return "shortValue";
			case 'I':
				return "intValue";
			case 'J':
				return "longValue";
			case 'F':
				return "floatValue";
			case 'D':
				return "doubleValue";
			default:
				throw new RuntimeException("unknown primitive type: " + t);
		}
	}

	/**
	 * Return the internal Java signature string of a given class.
	 *
	 * @param c given class type to convert
	 * @return internal Java signature of given class type
	 */
	public static String getSignature(Class c) {
		if (c.equals(Byte.TYPE))
			return "B";
		else if (c.equals(Character.TYPE))
			return "C";
		else if (c.equals(Double.TYPE))
			return "D";
		else if (c.equals(Float.TYPE))
			return "F";
		else if (c.equals(Integer.TYPE))
			return "I";
		else if (c.equals(Long.TYPE))
			return "J";
		else if (c.equals(Short.TYPE))
			return "S";
		else if (c.equals(Boolean.TYPE))
			return "Z";
		else if (c.equals(Void.TYPE))
			return "V";
		else if (c.isArray())
			return "[" + getSignature(c.getComponentType());
		else
			return 'L' + c.getName() + ';';
	}

	/**
	 * Return the internal Java signature string of a method.
	 *
	 * @param args method argument types
	 * @param ret  method return type
	 * @return internal Java signature of method with given
	 *         parameter and return types
	 */
	public static String getSignature(Class[] args, Class ret) {
		String sig = "";
		for (int i = 0; i < args.length; i++) {
			sig += getSignature(args[i]);
		}
		sig = "(" + sig + ")";
		if (ret != null) sig += getSignature(ret);
		return sig;
	}

	/**
	 * Return a method signature for given BCEL methodgen object.
	 *
	 * @param mg BCEL MethodGen object
	 * @return Java method signature
	 */
	public static String getSignature(MethodGen mg) {
		return mg.getClassName() + "." + mg.getName() +
		        mg.getSignature().replace('/', '.');
	}

	/**
	 * Return a method signature from an invocation instruction.
	 *
	 * @param ii  invocation instruction
	 * @param cpg class constant pool
	 * @return method signature
	 */
	public static String getSignature(InvokeInstruction ii, ConstantPoolGen cpg) {
		return getSignature(ii.getClassName(cpg), ii.getMethodName(cpg),
		        ii.getReturnType(cpg), ii.getArgumentTypes(cpg));
	}

	/**
	 * Return a method signature for given Java reflection method object.
	 *
	 * @param m Java reflection method object
	 * @return Java method signature
	 */
	public static String getSignature(java.lang.reflect.Member m) {
		if (m instanceof java.lang.reflect.Method) {
			java.lang.reflect.Method mm = (java.lang.reflect.Method) m;
			return mm.getDeclaringClass().getName() + "." + mm.getName() +
			        getSignature(mm.getParameterTypes(), mm.getReturnType());
		}
		if (m instanceof java.lang.reflect.Constructor) {
			java.lang.reflect.Constructor mm = (java.lang.reflect.Constructor) m;
			return mm.getDeclaringClass().getName() + "." + mm.getName() +
			        getSignature(mm.getParameterTypes(), null);
		}
		throw new RuntimeException("invalid member type: " + m);
	}

	/**
	 * Return a method signature for the given information.
	 *
	 * @param classname    class name
	 * @param methodname   method name
	 * @param methodreturn method return BCEL type
	 * @param methodargs   method argument BCEL types
	 * @return method signature string
	 */
	public static String getSignature(String classname, String methodname, Type methodreturn, Type[] methodargs) {
		return classname + "." + methodname +
		        Type.getMethodSignature(methodreturn, methodargs).replace('/', '.');
	}

	/**
	 * Return the name of the class used to store the continuation of a method at a given "program counter" point.
	 * The name needs to be a valid class name, to be in the same package as the method's class, to be unique
	 * among other (class, method name, method arguments, pc) values.
	 *
	 * @param mg method being continued
	 * @param pc "program counter" point within method
	 * @return name of class used to store continuation state
	 */
	public static String getContinutationClassName(MethodGen mg, int pc) {
		String pkgDot = mg.getClassName();
		int pkgIndex = pkgDot.lastIndexOf('.');
		String cl;
		if (pkgIndex == -1) {
			cl = pkgDot;
			pkgDot = "";
		} else {
			cl = pkgDot.substring(pkgIndex + 1);
			pkgDot = pkgDot.substring(0, pkgIndex + 1);
		}
		return pkgDot + Rewriter.CONTINUATION_STATE_PREFIX + "_"
		        + cl + "_" + mg.getName()
		        + "_" + Integer.toHexString(Util.escapeJavaIdent(mg.getSignature()).hashCode())
		        + "_" + pc;
	}

	/**
	 * Return the class name of the given Type or array type,
	 * or null for primitive types, or arrays of primitives.
	 *
	 * @param t BCEL Type object
	 * @return class name of type, or null for primitives
	 */
	public static String getTypeClassname(Type t) {
		if (t instanceof ObjectType) {
			return ((ObjectType) t).getClassName();
		} else if (t instanceof ArrayType) {
			return getTypeClassname(((ArrayType) t).getBasicType());
		} else {
			return null;
		}
	}

	/**
	 * Return the (byte) size of the constant pool.
	 *
	 * @param jcl BCEL Java class object
	 * @return size of constant pool in bytes
	 */
	public static int getConstantPoolSize(JavaClass jcl) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			jcl.getConstantPool().dump(new DataOutputStream(baos));
		} catch (IOException e) {
		}
		return baos.size();
	}

	//////////////////////////////////////////////////
	// runtime rewriting support functions
	//

	/**
	 * Prime the rewriter doing a breadth-first search loading
	 * (and rewrite) of all classes transitively statically referenced.
	 *
	 * @param classname root class of the breadth-first traversal
	 * @return number of classes loaded
	 * @throws ClassNotFoundException when requested or (directly or indirectly) referenced classes
	 *                                cannot be loaded
	 */
	public int prime(String classname) throws ClassNotFoundException {
		log.finer("** priming class rewriter starting with: " + classname);
		String[] list = getAllClassReferencesRecursively(classname);
		for (int i = 0; i < list.length; i++) {
			Class c = loadClass(list[i], false);
			// fire the static initializer (side-effect)
			try {
                c.getDeclaredField(Rewriter.STATIC_TRIGGER).get(null);
			} catch (Exception ex) {
				throw new RewriterException("invoking static triggers", ex);
			}
		}
		clearLookupCache();
		return list.length;
	}


	/**
	 * Install a new rewrite traversal handler.
	 *
	 * @param rewrite rewriter traversal handler
	 */
	public void installPreRewriteTraversal(ClassTraversal.Visitor rewrite) {
		rewriters.add(rewrite);
	}


	/**
	 * Convert class name into a filename.
	 *
	 * @param classname class name to converted
	 * @return file name containing given class
	 */
	public static String classToFileName(String classname) {
		return classname.replace('.', '/') + ".class";
	}

} // class: Rewriter



