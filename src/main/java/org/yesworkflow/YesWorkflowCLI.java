package org.yesworkflow;

/* This file is an adaptation of KuratorAkka.java in the org.kurator.akka
 * package as of 18Dec2014.
 */

import static java.util.Arrays.asList;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.List;

import org.yesworkflow.LanguageModel.Language;
import org.yesworkflow.comments.Comment;
import org.yesworkflow.exceptions.YWMarkupException;
import org.yesworkflow.exceptions.YWToolUsageException;
import org.yesworkflow.extract.DefaultExtractor;
import org.yesworkflow.extract.Extractor;
import org.yesworkflow.graph.DotGrapher;
import org.yesworkflow.graph.GraphView;
import org.yesworkflow.graph.Grapher;
import org.yesworkflow.model.DefaultModeler;
import org.yesworkflow.model.Modeler;
import org.yesworkflow.model.Program;
import org.yesworkflow.model.Workflow;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class YesWorkflowCLI {

    public static int YW_CLI_SUCCESS            =  0;
    public static int YW_UNCAUGHT_EXCEPTION     = -1;
    public static int YW_CLI_USAGE_EXCEPTION    = -2;
    public static int YW_MARKUP_EXCEPTION       = -3;

    public static final String EOL = System.getProperty("line.separator");

    public static void main(String[] args) throws Exception {

        Integer returnValue = null;

        try {
            returnValue = new YesWorkflowCLI().runForArgs(args);
        } catch (Exception e) {
            e.printStackTrace();
            returnValue = YW_UNCAUGHT_EXCEPTION;
        }

        System.exit(returnValue);
    }

    private PrintStream errStream;
    private PrintStream outStream;
    
    private OptionParser parser = null;
    private OptionSet options = null;
    
    private String command = null;
    private String sourceFilePath = null;
    private String commentDelimiter = null;
    
    private Extractor extractor = null;
    private Modeler modeler = null;
    private Grapher grapher = null;
    
    private List<Comment> comments;
    private Program model = null;

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

    public YesWorkflowCLI modeler(Modeler modeler) {
        this.modeler = modeler;
        return this;
    }

    public YesWorkflowCLI grapher(Grapher grapher) {
        this.grapher = grapher;
        return this;
    }

    public int runForArgs(String[] args) throws Exception {

        initialize();

        try {

            // parse the command line arguments and options
            try {
                options = parser.parse(args);
            } catch (OptionException exception) {
                throw new YWToolUsageException("ERROR: " + exception.getMessage());
            }

            // print help and exit if requested
            if (options.has("h")) {
                printCLIHelp();
                return YW_CLI_SUCCESS;
            }

            // extract YesWorkflow command from arguments
            extractCommandFromOptions();
            if (command == null) {
                throw new YWToolUsageException("ERROR: No command provided to YesWorkflow");
            }

            // extract remaining arguments
            extractSourcePathFromOptions();
            extractCommentDelimiter();

            // run extractor and exit if extract command given
            if (command.equals("extract")) {
                extract();
                return YW_CLI_SUCCESS;
            }

            if (command.equals("graph")) {
                extract();
                model();
                graph();
                return YW_CLI_SUCCESS;
            }

        } catch (YWToolUsageException e) {
            printToolUsageErrors(e.getMessage());
            printCLIHelp();
            return YW_CLI_USAGE_EXCEPTION;
        } catch (YWMarkupException e) {
            printMarkupErrors(e.getMessage());
            return YW_MARKUP_EXCEPTION;
        } 

        return YW_CLI_SUCCESS;
    }
    
    private void printMarkupErrors(String message) {
        errStream.println();
        errStream.println("******************* YESWORKFLOW MARKUP ERRORS **************************");
        errStream.println();
        errStream.print(message);
        errStream.println();
        errStream.println("------------------------------------------------------------------------");
    }

    private void printToolUsageErrors(String message) {
        errStream.println();
        errStream.println("****************** YESWORKFLOW TOOL USAGE ERRORS ***********************");
        errStream.println();
        errStream.println(message);
    }
    
    private void printCLIHelp() throws IOException {
        errStream.println();
        errStream.println("---------------------- YesWorkflow usage summary -----------------------");
        errStream.println();
        parser.printHelpOn(errStream);
        errStream.println();
        errStream.println("------------------------------------------------------------------------");
    }
    
    private void initialize() {
        options = null;
        command = null;
        sourceFilePath = null;
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

    private void extractCommentDelimiter() {        
    	if(options.hasArgument("x")) {
       		commentDelimiter = (String)options.valueOf("x");
    	}
    }
    
    private void extractSourcePathFromOptions() {
    	sourceFilePath = (String) options.valueOf("s");
    }
    
    private GraphView extractGraphView() throws YWToolUsageException {
        
        String viewString = (String) options.valueOf("v");
        
        if (viewString.equalsIgnoreCase("process"))     return GraphView.PROCESS_CENTRIC_VIEW;
        if (viewString.equalsIgnoreCase("data"))        return GraphView.DATA_CENTRIC_VIEW;
        if (viewString.equalsIgnoreCase("combined"))    return GraphView.COMBINED_VIEW;
        
        throw new YWToolUsageException("Unsupported graph view: " + viewString);
    }

    private OptionParser createOptionsParser() throws Exception {

        OptionParser parser = null;

        parser = new OptionParser() {{

            acceptsAll(asList("c", "command"), "command to YesWorkflow")
                .withRequiredArg()
                .ofType(String.class)
                .describedAs("command");

            acceptsAll(asList("x", "commchar"), "comment character")
        		.withOptionalArg()
        		.ofType(String.class)
        		.describedAs("comment");

            acceptsAll(asList("s", "source"), "path to source file to analyze")
                .withOptionalArg()
                .defaultsTo("-")
                .ofType(String.class)
                .describedAs("script");

            acceptsAll(asList("g", "graph"), "path to graphviz dot file for storing rendered workflow graph")
                .withOptionalArg()
                .defaultsTo("-")
                .ofType(String.class)
                .describedAs("dot file");

            acceptsAll(asList("v", "view"), "view of model to render as a graph")
                .withRequiredArg()
                .ofType(String.class)
                .defaultsTo("process")
                .describedAs("process|data|combined");

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
           extractor = new DefaultExtractor(this.outStream, this.errStream);
        }

        if (sourceFilePath.equals("-")) {
        	extractor.sourceReader(new InputStreamReader(System.in));
        } else {
        	extractor.sourcePath(sourceFilePath);
        }
        
        if (commentDelimiter != null) {
            extractor.commentDelimiter(commentDelimiter);
        } else {
            
            Language language = LanguageModel.languageForFileName(sourceFilePath);
            if (language != null) {
                extractor.languageModel(new LanguageModel(language));
            } else {
                throw new YWToolUsageException("Cannot identify language of source file.  Please specify a comment character.");
            }
        }
        
        extractor.extract();

        if (options.has("l")) {

            StringBuffer linesBuffer = new StringBuffer();
            for (String line : extractor.getLines()) {
                linesBuffer.append(line);
                linesBuffer.append(EOL);
            }

            writeTextToOptionNamedFile("l", linesBuffer.toString());
        }
        
        comments = extractor.getComments();
    }

    public void model() throws Exception {
        
        if (modeler == null) {
            modeler = new DefaultModeler(this.outStream, this.errStream);
         }

        model = (Program) modeler.comments(comments)
                                 .model()
                                 .getModel();
    }

    public void graph() throws Exception {

        GraphView view = extractGraphView();

        if (grapher == null) {
            grapher = new DotGrapher(this.outStream, this.errStream);
         }
        
        String graph = grapher.workflow((Workflow)model)
                              .view(view)
                              .graph()
                              .toString();

        writeTextToOptionNamedFile("g", graph);
    }

    public void writeTextToOptionNamedFile(String option, String text) throws IOException {
        String path = (String) options.valueOf(option);
        PrintStream linesOutputStream = (path.equals("-")) ? outStream : new PrintStream(path);
        linesOutputStream.print(text);
        if (linesOutputStream != outStream) {
            linesOutputStream.close();
        }
    }
}
