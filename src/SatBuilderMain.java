import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.spi.BooleanOptionHandler;

import algorithms.automaton_builders.CryptominisatAutomatonBuilder;
import structures.Automaton;
import structures.ScenarioTree;

public class SatBuilderMain {

	@Argument(usage = "paths to files with scenarios", metaVar = "files", required = true)
	private List<String> arguments = new ArrayList<String>();

	@Option(name = "--size", aliases = { "-s" }, usage = "automaton size", metaVar = "<size>", required = true)
	private int size;

	@Option(name = "--log", aliases = { "-l" }, usage = "write log to this file", metaVar = "<file>")
	private String logFilePath;

	@Option(name = "--result", aliases = { "-r" }, usage = "write result automaton in GV format to this file", metaVar = "<GV file>")
	private String resultFilePath;

	@Option(name = "--tree", aliases = { "-t" }, usage = "write scenarios tree in GV format to this file", metaVar = "<GV file>")
	private String treeFilePath;

	@Option(name = "--model", aliases = { "-m" }, usage = "write SAT model to this file", metaVar = "<file>")
	private String modelFilePath;

	@Option(name = "--complete", aliases = { "-c" }, handler = BooleanOptionHandler.class, usage = "TODO: is automaton will be complete")
	private boolean isComplete;

	private void launcher(String[] args) throws IOException {
		Locale.setDefault(Locale.US);

		CmdLineParser parser = new CmdLineParser(this);
		try {
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			System.out.println("Extended finite state machine CryptoMiniSat based induction tool");
			System.out.println("Author: Vladimir Ulyantsev (ulyantsev@rain.ifmo.ru)\n");
			System.out.print("Usage: ");
			parser.printSingleLineUsage(System.out);
			System.out.println();
			parser.printUsage(System.out);
			return;
		}
		
		try {
			Runtime.getRuntime().exec("cryptominisat");
		} catch (Exception e) {
			System.err.println("ERROR: Problems with CryptoMiniSat execution");
			e.printStackTrace();
			return;
		}

		Logger logger = Logger.getLogger("Logger");
		if (logFilePath != null) {
			try {
				FileHandler fh = new FileHandler(logFilePath, false);
				logger.addHandler(fh);
				SimpleFormatter formatter = new SimpleFormatter();
				fh.setFormatter(formatter);

				logger.setUseParentHandlers(false);
				System.out.println("Log redirected to " + logFilePath);
			} catch (Exception e) {
				System.err.println("Can't work with file " + logFilePath + ": " + e.getMessage());
				return;
			}
		}

		ScenarioTree tree = new ScenarioTree();
		for (String filePath : arguments) {
			try {
				tree.load(filePath);
				logger.info("Loaded scenarios from " + filePath);
				logger.info("  Total scenarios tree size: " + tree.nodeCount());
			} catch (Exception e) {
				logger.warning("Can't load scenarios from file " + filePath);
				return;
			}
		}

		if (treeFilePath != null) {
			try {
				PrintWriter treePrintWriter = new PrintWriter(new File(treeFilePath));
				treePrintWriter.println(tree);
				treePrintWriter.close();
				logger.info("Scenarios tree saved to " + treeFilePath);
			} catch (Exception e) {
				logger.warning("Can't save scenarios tree to " + treeFilePath);
			}
		}

		PrintWriter modelPrintWriter = null;
		if (modelFilePath != null) {
			try {
				modelPrintWriter = new PrintWriter(new File(modelFilePath));
				logger.info("SAT model file " + modelFilePath);
			} catch (FileNotFoundException e) {
				logger.warning("File " + modelFilePath + " not found: " + e.getMessage());
			}
		}

		long startTime = System.currentTimeMillis();
		logger.info("Start building automaton with CryptoMiniSat");
		Automaton resultAutomaton;

		if (modelPrintWriter == null) {
			resultAutomaton = CryptominisatAutomatonBuilder.build(tree, size);
		} else {
			resultAutomaton = CryptominisatAutomatonBuilder.build(tree, size, modelPrintWriter);
			modelPrintWriter.close();
		}
		double executionTime = (System.currentTimeMillis() - startTime) / 1000.;

		if (resultAutomaton == null) {
			logger.info("Automaton with " + size + " states NOT FOUND!");
		} else {
			logger.info("Automaton with " + size + " states WAS FOUND!");
			if (resultFilePath != null) {
				try {
					PrintWriter resultPrintWriter = new PrintWriter(new File(resultFilePath));
					resultPrintWriter.println(resultAutomaton);
					resultPrintWriter.close();
				} catch (FileNotFoundException e) {
					logger.warning("File " + resultFilePath + " not found: " + e.getMessage());
				}
			}
		}
		logger.info("SAT-solver automaton builder execution time: " + executionTime);
	}

	public void run(String[] args) {
		try {
			launcher(args);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public static void main(String[] args) {
		new SatBuilderMain().run(args);
	}

}
