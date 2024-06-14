package org.jcyclone.core.cfg;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Allows the configuration to be set from a file that is passed in  
 * @see JCycloneConfig
 * @author graham miller
 */
public class FileConfig extends JCycloneConfig
{
	public FileConfig() {
		super();
	}

	/**
	 * Create a new FileConfig, reading the configration from the
	 * given file. The configuration file uses an XML-like structure;
	 * see the JCyclone documentation for more information on its format.
	 *
	 * @param defaultArgs Default initialization arguments passed to
	 *                    every stage. These override any arguments found in the config file.
	 *                    Each element of this array must be a string with the format
	 *                    <tt>"key=value"</tt>.
	 */
	public FileConfig(String fname, String defaultArgs[]) throws IOException {
		this(defaultArgs);
		readFile(fname);
	}

	/**
	 * Create a new FileConfig, reading the configration from the
	 * given file. The configuration file uses an XML-like structure;
	 * see the JCyclone documentation for more information on its format.
	 */
	public FileConfig(String fname) throws IOException {
		this();
		readFile(fname);
	}

	public FileConfig(String[] defaultArgs) throws IOException {
		super(defaultArgs);
	}

	/**
	 * Read the configuration from the given file.
	 */
	public void readFile(String fname) throws IOException {
	
		root = ParserFactory.createParser(fname).parse();
	
		ConfigSection global_initargs = null;
	
		if (!root.getName().equals("jcyclone"))
			throw new IOException("Outermost section config file named " + root.getName() + ", expecting jcyclone");
	
		// Set default values
		for (int i = 0; i < defaults.length; i += 2) {
			String key = defaults[i];
			String val = defaults[i + 1];
			if (getString(key) == null) {
				putString(key, val);
			}
		}
	
		// Set command line values
		this.defaultInitArgs = new Hashtable();
		if (cmdLineArgs != null) {
			Enumeration e = cmdLineArgs.keys();
			while (e.hasMoreElements()) {
				String key = (String) e.nextElement();
				if (key.indexOf('.') != -1) {
					putString(key, (String) cmdLineArgs.get(key));
				} else {
					this.defaultInitArgs.put(key, (String) cmdLineArgs.get(key));
				}
			}
		}
	
		if (DEBUG) {
			System.err.println("DOING DUMP: -----------------------");
			root.dump();
			System.err.println("DONE WITH DUMP ---------------------");
		}
	
	}

	static class ParserFactory {
		static IParser createParser(String fileName) throws IOException {
			if (fileName.endsWith(".cfg"))
				return new CfgParser(fileName);
			else
				throw new IOException("File extension not supported");
		}
	}

	interface IParser {
		ConfigSection parse() throws IOException;
	}

	static class CfgParser implements IParser {

		private StreamTokenizer tok;

		CfgParser(String fileName) throws IOException {
			Reader in = new CfgReader(fileName);
			tok = new StreamTokenizer(in);
			tok.resetSyntax();
			tok.wordChars((char) 0, (char) 255);
			tok.whitespaceChars('\u0000', '\u0020');
			tok.commentChar('#');
			tok.eolIsSignificant(true);
		}

		public ConfigSection parse() throws IOException {
			return parse(null);
		}

		// Read next section name, parse recursively until we see the
		// end of that section
		private ConfigSection parse(ConfigSection parentSec) throws IOException {
			String word, key, value;
			ConfigSection sec = null;
			String secname;

			// Get initial section name
			word = nextWord();
			if (word.startsWith("<") && word.endsWith(">")) {
				secname = word.substring(1, word.length() - 1);
				sec = new ConfigSection(secname);
			} else {
				throw new IOException("No section name found at line " + tok.lineno() + " of config file, read " + word);
			}

			boolean done = false;
			while (!done) {

				key = null;
				while (true) {
					// Read key
					word = nextWord();
					if (word.startsWith("<") && word.endsWith(">")) {
						String val = word.substring(1, word.length() - 1);
						if (val.equals("/" + secname)) {
							// Done reading this section
							done = true;
							break;
						} else {
							// Found a new section; recurse
							tok.pushBack();
							tok.wordChars('0', '9');  // XXX Why we have this statement???

							ConfigSection subsec = parse(sec);
							if (sec.getSubsection(subsec.getName()) != null) {
								throw new IOException("subsection " + subsec.getName() + " redefined at line " + tok.lineno() + " of config file");
							}
							if (sec.getVal(subsec.getName()) != null) {
								throw new IOException("subsection " + subsec.getName() + " conflicts with key " + subsec.getName() + " at line " + tok.lineno() + " of config file");
							}
							sec.addSubsection(subsec);
						}
					} else {
						key = word;
						break;
					}
				}

				if (done) break;

				// Read value
				word = nextLine();
				if (word.startsWith("<") && word.endsWith(">")) {
					// Bad format: Should not have section tag here
					throw new IOException("Unexpected section tag " + word + " on line " + tok.lineno() + " of config file");
				} else {
					value = word;
				}

				if (key == null) throw new IOException("key is null at line " + tok.lineno() + " of config file");
				if (sec.getVal(key) != null) {
					throw new IOException("key " + key + " redefined at line " + tok.lineno() + " of config file");
				}
				if (sec.getSubsection(key) != null) {
					throw new IOException("key " + key + " conflicts with subsection " + key + " at line " + tok.lineno() + " of config file");
				}
				if (key.indexOf(DELIM_CHAR) != -1) {
					throw new IOException("key " + key + " may not contain character '" + DELIM_CHAR + "' at line " + tok.lineno() + " of config file");
				}
				sec.putVal(key, value);
			}
			return sec;
		}

		// Read next whitespace-delimited word from tok
		private String nextWord() throws IOException {
			while (true) {
				int type = tok.nextToken();
				switch (type) {

					case StreamTokenizer.TT_EOL:
						continue;

					case StreamTokenizer.TT_EOF:
						throw new EOFException("EOF in config file");

					case StreamTokenizer.TT_WORD:
						if (DEBUG) System.err.println("nextWord returning " + tok.sval);
						return tok.sval;

					case StreamTokenizer.TT_NUMBER:
						if (DEBUG) System.err.println("nextWord returning number");
						return Double.toString(tok.nval);

					default:
						continue;
				}
			}
		}

		// Read rest of line from tok
		private String nextLine() throws IOException {
			String line = new String("");
			boolean first = true;

			while (true) {
				switch (tok.nextToken()) {

					case StreamTokenizer.TT_EOL:
						if (DEBUG) System.err.println("nextLine returning " + line);
						return line;

					case StreamTokenizer.TT_EOF:
						throw new EOFException("EOF in config file");

					case StreamTokenizer.TT_WORD:
						if (first) {
							line = tok.sval;
							first = false;
						} else {
							line += " " + tok.sval;
						}
						break;

					case StreamTokenizer.TT_NUMBER:
						if (first) {
							line = Double.toString(tok.nval);
							first = false;
						} else {
							line += " " + Double.toString(tok.nval);
						}
						break;

					default:
						continue;
				}
			}
		}
	}

	static class CfgReader extends DirectiveReader {
		public CfgReader(String fname) throws IOException {
			super(new PropertyReader(initProperties(fname),
			    new IgnoreCommentReader(new BufferedReader(new FileReader(fname)))));
		}

		static Map initProperties(String fname) {
			// init system properties
			Map<String,String> properties = new HashMap<String,String>();
			properties.put("config.home", new File(fname).getParent());
			properties.put("user.home", System.getProperty("user.home"));
			return properties;
		}

		protected Reader createReader(String fname) throws IOException {
			return new CfgReader(fname);
		}
	}

	abstract static class BaseFilterReader extends FilterReader {

		public BaseFilterReader(Reader in) {
			super(in);
		}

		public int read(char cbuf[], int off, int len) throws IOException {
			int i = 0;
			while (i < len) {
				int c = read();
				if (c == -1) return i;
				cbuf[off + i] = (char) c;
				i++;
			}
			return i;
		}
	}

	/**
	 * Internal class to preprocess special directives in the
	 * config file.
	 * XXX : (jm) Reader must be thread-safe.
	 * Use super.lock object to synchronize operations.
	 */
	static abstract class DirectiveReader extends BaseFilterReader {
		private Reader includedFile, markStream;
		private boolean markIsIncluded = false, closed = false;

		DirectiveReader(Reader under) throws IOException {
			super(under);
			if (!under.markSupported()) {
				throw new IOException("JCycloneConfig: Internal error: directiveReader.under must support mark() -- contact mdw@cs.berkeley.edu");
			}
		}

		public int read() throws IOException {
			if (closed) throw new IOException("directiveReader is closed");
			if (includedFile != null) {
				int ret = includedFile.read();
				if (ret == -1)
					includedFile = null;
				else
					return ret;
			}

			while (true) {

				int c = in.read();

				if (c == '<') {
					in.mark(100);
					if (in.read() == '!') {
						// Process special directive; read until '>'
						String directive = "<!";
						char c1 = ' ';
						while (c1 != '>') {
							try {
								c1 = (char) in.read();
								if (c1 == -1) throw new IOException("End of file");
							} catch (IOException ioe) {
								throw new IOException("JCycloneConfig: Unterminated directive " + directive.substring(0, Math.min(directive.length(), 10)) + " in configuration file");
							}
							directive += c1;
						}
						if (DEBUG) System.err.println("Got special directive: " + directive);

						if (directive.startsWith("<!include")) {
							StringTokenizer st = new StringTokenizer(directive);
							String dir = st.nextToken();
							String fname = st.nextToken();
							fname = fname.substring(0, fname.length() - 1).trim();
							if (DEBUG) System.err.println("Including file: " + fname);
							includedFile = createReader(fname);
							int ret = includedFile.read();
							if (ret == -1) {
								includedFile = null;
								continue;
							} else {
								return ret;
							}
						} else {
							throw new IOException("JCycloneConfig: Unrecognized directive " + directive + " in config file");
						}

					} else {
						// Got a '<' with no following '!'
						in.reset();
						return c;
					}
				} else {
					// Got something other than '<'
					return c;
				}
			}
		}

		protected abstract Reader createReader(String fname) throws IOException;

		public boolean ready() throws IOException {
			if (includedFile != null) return includedFile.ready();
			return in.ready();
		}

		public boolean markSupported() {
			return true;
		}

		public void mark(int readAheadLimit) throws IOException {
			if (includedFile != null) {
				markStream = includedFile;
				markIsIncluded = true;
			} else {
				markStream = in;
			}
			markStream.mark(readAheadLimit);
		}

		public void reset() throws IOException {
			markStream.reset();
			if (markIsIncluded) includedFile = markStream;
		}

		public void close() throws IOException {
			if (includedFile != null) includedFile.close();
			in.close();
			closed = true;
		}
	}

	static class IgnoreCommentReader extends BaseFilterReader {

		static final int COMMENT_START = '#';
		static final int COMMENT_END = '\n';

		public IgnoreCommentReader(Reader in) {
			super(in);
		}

		public int read() throws IOException {
			int c = in.read();
			// Ignore characters inside of comment
			if (c == COMMENT_START) {
				do {
					c = in.read();
				} while (c != COMMENT_END);
				return read();
			}
			return c;
		}

	}

	static class PropertyReader extends BaseFilterReader {

		static final int MAX_PROPERTY_NAME_LENGTH = 100;

		String propVal;
		int index;
		Map properties;

		public PropertyReader(Map properties, Reader in) {
			super(in);
			this.properties = properties;
		}

		public int read() throws IOException {

			if (propVal != null) {
				if (index >= propVal.length()) {
					index = 0;
					propVal = null;
				} else
					return propVal.charAt(index++);
			}

			while (true) {

				int c = in.read();

				if (c == '$') {
					in.mark(MAX_PROPERTY_NAME_LENGTH);
					if (in.read() == '{') {
						// Process special property; read until '}'
						StringBuffer propBuf = new StringBuffer();
						char c1 = ' ';
						while (true) {
							try {
								c1 = (char) in.read();
								if (c1 == -1) throw new IOException("End of file");
								if (c1 == '}') break;
								propBuf.append(c1);
							} catch (IOException ioe) {
								throw new IOException("JCycloneConfig: Unterminated property " + propBuf.substring(0, Math.min(propBuf.length(), 10)) + " in configuration file");
							}
						}

						String propName = propBuf.toString();
						if (DEBUG) System.err.println("Got special property: " + propName);

						propVal = (String) properties.get(propName);

						if (propVal != null) {
							return propVal.charAt(index++);
						} else
							throw new IOException("JCycloneConfig: Unrecognized property '" + propName + "' in config file");

					} else {
						// Got a '$' with no following '{'
						in.reset();
						return c;
					}
				} else {
					// Got something other than '$'
					return c;
				}
			}
		}

	}

}
