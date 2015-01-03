package org.yesworkflow;

/* This file is an adaptation of KuratorAkka.java in the org.kurator.akka
 * package as of 18Dec2014.
 */

import static java.util.Arrays.asList;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

import org.yesworkflow.exceptions.UsageException;
import org.yesworkflow.extract.DefaultExtractor;
import org.yesworkflow.extract.Extractor;
import org.yesworkflow.graph.DotGrapher;
import org.yesworkflow.graph.GraphType;
import org.yesworkflow.model.Program;
import org.yesworkflow.model.Workflow;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class YesWorkflowCLI {

    public static int YW_CLI_SUCCESS = 0;
    public static int YW_CLI_USAGE_ERROR = -1;
    public static int YW_CLI_UNCAUGHT_EXCEPTION = -2;

    public static final String EOL = System.getProperty("line.separator");

    public static void main(String[] args) throws Exception {
        
        Integer returnValue = null;

        try {
            returnValue = new YesWorkflowCLI().runForArgs(args);
        } catch (Exception e) {
            e.printStackTrace();
            returnValue = YW_CLI_UNCAUGHT_EXCEPTION;
        }
        
        System.exit(returnValue);
    }
    
    private PrintStream errStream;
    private OptionParser parser = null;
    private OptionSet options = null;
    private String command = null;
    private String sourceFilePath = null;
    private String databaseFilePath = null;
    private Extractor extractor = null;
    private PrintStream outStream;

    public YesWorkflowCLI() throws Exception {
        this(System.out, System.err);
    }

    public YesWorkflowCLI(PrintStream outStream, PrintStream errStream) throws Exception {
        this.outStream = outStream;
        this.errStream = errStream;
        this.parser = createOptionsParser();
    }

    public YesWorkflowCLI extractor(Extractor extractor) {
        this.extractor = extractor;
        return this;
    }

    public int runForArgs(String[] args) throws Exception {
        
        initialize();
        
        try {

            // parse the command line arguments and options
            try {
                options = parser.parse(args);
            } catch (OptionException exception) {
                throw new UsageException(exception.getMessage());
            }
            
            // print help and exit if requested
            if (options.has("h")) {
                errStream.println();
                parser.printHelpOn(errStream);
                return YW_CLI_SUCCESS;            
            }
            
            // extract YesWorkflow command from arguments
            extractCommandFromOptions();                            
            if (command == null) {
                throw new UsageException("No command provided to YesWorkflow");
            }
        
            // extract remaining arguments
            extractSourcePathFromOptions();
            extractDatabasePathFromOptions();

            // run extractor and exit if extract command given
            if (command.equals("extract")) {
                extract();
                return YW_CLI_SUCCESS;
            }

            if (command.equals("graph")) {
                extract();
                graph();
                return YW_CLI_SUCCESS;
            }
            
            
        } catch (UsageException ue) {
            errStream.print("Usage error: ");
            errStream.println(ue.getMessage());
            errStream.println();
            parser.printHelpOn(errStream);
            return YW_CLI_USAGE_ERROR;
        }
        
        return YW_CLI_SUCCESS;
    }
    
    private void initialize() {
        options = null;
        command = null;
        sourceFilePath = null;
        databaseFilePath = null;
    }
    
    private void extractCommandFromOptions() {

        if (options.nonOptionArguments().size() == 1) {
            
            // if there is only one non-option argument assume this is the command to YesWorkflow
            command = (String) options.nonOptionArguments().get(0);

        } else if (options.hasArgument("c")) {

            // otherwise use the argument to the -c option
            command = (String) options.valueOf("c");
        }
    }

    private void extractDatabasePathFromOptions() {
        if (options.hasArgument("d")) {
            databaseFilePath = (String) options.valueOf("d");
        }
    }

    private void extractSourcePathFromOptions() {
    	sourceFilePath = (String) options.valueOf("s");
    }

    private OptionParser createOptionsParser() throws Exception {

        OptionParser parser = null;
        
        parser = new OptionParser() {{
            
            acceptsAll(asList("c", "command"), "command to YesWorkflow")
                .withRequiredArg()
                .ofType(String.class)
                .describedAs("command");

            acceptsAll(asList("s", "source"), "path to source file to analyze")
                .withOptionalArg()
                .defaultsTo("-")
                .ofType(String.class)
                .describedAs("script");

            acceptsAll(asList("d", "database"), "path to database file for storing extracted workflow graph")
                .withRequiredArg()
                .ofType(String.class)
                .describedAs("database");

            acceptsAll(asList("g", "graph"), "path to graphviz dot file for storing rendered workflow graph")
                .withOptionalArg()
                .defaultsTo("-")
                .ofType(String.class)
                .describedAs("dot file");
                            
            acceptsAll(asList("l", "lines"), "path to file for saving extracted comment lines")
                .withOptionalArg()
                .defaultsTo("-")
                .ofType(String.class)
                .describedAs("lines file");

            acceptsAll(asList("h", "help"), "display help");

        }};
            
        return parser;
    }
    
    public void extract() throws Exception {
        
        if (extractor == null) {
           extractor = new DefaultExtractor();
        }
        
        if (sourceFilePath.equals("-")) {
        	extractor.sourceReader(new InputStreamReader(System.in));
        } else {
        	extractor.sourcePath(sourceFilePath);
        }
        
        extractor.databasePath(databaseFilePath)
                 .commentCharacter('#')
                 .extract();
        
        if (options.has("l")) {
            
            StringBuffer linesBuffer = new StringBuffer();
            for (String line : extractor.getLines()) {
                linesBuffer.append(line);
                linesBuffer.append(EOL);
            }
            
            writeTextToOptionNamedFile("l", linesBuffer.toString());
        }
    }
    
    public void writeTextToOptionNamedFile(String option, String text) throws IOException {
        String path = (String) options.valueOf(option);
        PrintStream linesOutputStream = (path.equals("-")) ? outStream : new PrintStream(path);
        linesOutputStream.print(text);
        if (linesOutputStream != outStream) {
            linesOutputStream.close();
        }
    }

    public void graph() throws Exception {
        
        Program program = extractor.getProgram();
        
        String graph = new DotGrapher()
            .workflow((Workflow)program)
            .type(GraphType.DATA_FLOW_GRAPH)
            .graph()
            .toString();
        
        writeTextToOptionNamedFile("g", graph);
    }
}