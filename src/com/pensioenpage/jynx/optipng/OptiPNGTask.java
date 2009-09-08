// Copyright 2007-2009, PensioenPage B.V.
package com.pensioenpage.jynx.optipng;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import static org.apache.tools.ant.Project.MSG_ERR;
import static org.apache.tools.ant.Project.MSG_VERBOSE;
import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.taskdefs.ExecuteStreamHandler;
import org.apache.tools.ant.taskdefs.ExecuteWatchdog;
import org.apache.tools.ant.taskdefs.MatchingTask;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.util.FileUtils;

/**
 * An Apache Ant task for optimizing a number of PNG files, using OptiPNG. For
 * more information, see the
 * <a href="http://optipng.sourceforge.net/">OptiPNG homepage</a>.
 *
 * <p>The most notable parameters supported by this task are:
 *
 * <dl>
 * <dt>command
 * <dd>The name of the command to execute.
 *     Optional, defaults to <code>optipng</code>.
 *
 * <dt>timeOut
 * <dd>The time-out for each individual invocation of the command, in
 *     milliseconds. Optional, defaults to 60000 (60 seconds).
 *
 * <dt>dir
 * <dd>The source directory to read from.
 *     Optional, defaults to the project base directory.
 *
 * <dt>includes
 * <dd>The files to match in the source directory.
 *     Optional, defaults to all files.
 *
 * <dt>excludes
 * <dd>The files to exclude, even if they are matched by the include filter.
 *     Optional, default is empty.
 *
 * <dt>toDir
 * <dd>The target directory to write to.
 *     Optional, defaults to the source directory.
 * </dl>
 *
 * <p>This task supports more parameters and contained elements, inherited
 * from {@link MatchingTask}. For more information, see
 * <a href="http://ant.apache.org/manual/dirtasks.html">the Ant site</a>.
 *
 * @author <a href="mailto:ernst@pensioenpage.com">Ernst de Haan</a>
 */
public final class OptiPNGTask extends MatchingTask {

   //-------------------------------------------------------------------------
   // Class fields
   //-------------------------------------------------------------------------

   /**
    * The name of the default OptiPNG command: <code>"optipng"</code>.
    */
   public static final String DEFAULT_COMMAND = "optipng";

   /**
    * The default time-out: 60 seconds.
    */
   public static final long DEFAULT_TIMEOUT = 60L * 1000L;


   //-------------------------------------------------------------------------
   // Class functions
   //-------------------------------------------------------------------------

   /**
    * Returns a quoted version of the specified string,
    * or <code>"(null)"</code> if the argument is <code>null</code>.
    *
    * @param s
    *    the character string, can be <code>null</code>,
    *    e.g. <code>"foo bar"</code>.
    *
    * @return
    *    the quoted string, e.g. <code>"\"foo bar\""</code>,
    *    or <code>"(null)"</code> if the argument is <code>null</code>.
    */
   private static final String quote(String s) {
      return s == null ? "(null)" : "\"" + s + '"';
   }

   /**
    * Returns a quoted version string representation,
    * or <code>"(null)"</code> if the argument is <code>null</code>.
    *
    * @param o
    *    the object, can be <code>null</code>.
    *
    * @return
    *    the quoted string representation of the specified object,
    *    e.g. <code>"\"foo bar\""</code>,
    *    or <code>"(null)"</code> if the argument is <code>null</code>.
    */
   private static final String quote(Object o) {
      return o == null ? "(null)" : quote(o.toString());
   }

   /**
    * Determines if the specified character string matches the regular
    * expression.
    *
    * @param s
    *    the string to research, or <code>null</code>.
    *
    * @param regex
    *    the regular expression, cannot be <code>null</code>.
    *
    * @return
    *    <code>true</code> if <code>s</em> matches the regular expression;
    *    <code>false</code> if it does not.
    *
    * @throws IllegalArgumentException
    *    if <code>regex == null</code> or if it has an invalid syntax. 
    */
   private static final boolean matches(String s, String regex)
   throws IllegalArgumentException {

      // Check preconditions
      if (regex == null) {
         throw new IllegalArgumentException("regex == null");
      }

      // Compile the regular expression pattern
      Pattern pattern;
      try {
         pattern = Pattern.compile(regex);
      } catch (PatternSyntaxException cause) {
         throw new IllegalArgumentException("Invalid regular expression \"" + regex + "\".", cause);
      }

      // Short-circuit if the string is null
      if (s == null) {
         return false;
      }

      // Find a match
      return pattern.matcher(s).find();
   }

   /**
    * Checks if the specified string is either null or empty (after trimming
    * the whitespace off).
    *
    * @param s
    *    the string to check.
    *
    * @return
    *    <code>true</code> if <code>s == null || s.trim().length() &lt; 1</code>;
    *    <code>false</code> otherwise.
    */
   private static final boolean isEmpty(String s) {
      return s == null || s.trim().length() < 1;
   }

   /**
    * Checks if the specified abstract path name refers to an existing
    * directory.
    *
    * @param description
    *    the description of the directory, cannot be <code>null</code>.
    *
    * @param path
    *    the abstract path name as a {@link File} object.
    *
    * @param mustBeReadable
    *    <code>true</code> if the directory must be readable.
    *
    * @param mustBeWritable
    *    <code>true</code> if the directory must be writable.
    *
    * @throws IllegalArgumentException
    *    if <code>location == null
    *          || {@linkplain TextUtils}.{@linkplain TextUtils#isEmpty(String) isEmpty}(description)</code>.
    *
    * @throws BuildException
    *    if <code>  path == null
    *          || ! path.exists()
    *          || ! path.isDirectory()
    *          || (mustBeReadable &amp;&amp; !path.canRead())
    *          || (mustBeWritable &amp;&amp; !path.canWrite())</code>.
    */
   private static final void checkDir(String  description,
                                      File    path,
                                      boolean mustBeReadable,
                                      boolean mustBeWritable)
   throws IllegalArgumentException, BuildException {

      // Check preconditions
      if (isEmpty(description)) {
         throw new IllegalArgumentException("description is empty (" + quote(description) + ')');
      }

      // Make sure the path refers to an existing directory
      if (path == null) {
         throw new BuildException(description + " is not set.");
      } else if (! path.exists()) {
         throw new BuildException(description + " (\"" + path + "\") does not exist.");
      } else if (! path.isDirectory()) {
         throw new BuildException(description + " (\"" + path + "\") is not a directory.");

      // Make sure the directory is readable, if that is required
      } else if (mustBeReadable && (! path.canRead())) {
         throw new BuildException(description + " (\"" + path + "\") is not readable.");

      // Make sure the directory is writable, if that is required
      } else if (mustBeWritable && (! path.canWrite())) {
         throw new BuildException(description + " (\"" + path + "\") is not writable.");
      }
   }


   //-------------------------------------------------------------------------
   // Constructors
   //-------------------------------------------------------------------------

   /**
    * Constructs a new <code>OptiPNGTask</code> object.
    */
   public OptiPNGTask() {
      // empty
   }


   //-------------------------------------------------------------------------
   // Fields
   //-------------------------------------------------------------------------

   /**
    * The directory to read the image files from.
    * See {@link #setDir(File)}.
    */
   private File _sourceDir;

   /**
    * The directory to write <code>.css</code> files to.
    * See {@link #setToDir(File)}.
    */
   private File _destDir;

   /**
    * The command to execute. If unset, then this task will attempt to find a
    * proper executable by itself.
    */
   private String _command;

   /**
    * The time-out to apply, in milliseconds, or 0 (or lower) in case no
    * time-out should be applied.
    */
   private long _timeOut;

   /**
    * Character string that indicates whether the files should be processed
    * with OptiPNG at all. There are 3 options:
    * <dl>
    * <dt><code>"yes"</code> or <code>"true"</code>
    * <dd>The files <em>must</em> be processed by OptiPNG.
    *     If at least one file could not be processed, for example because
    *     OptiPNG is unavailable or because there are unrecoverable errors,
    *     then this task fails.
    *
    * <dt><code>"no"</code> or <code>"false"</code>
    * <dd>The files must <em>not</em> be processed by OptiPNG but must instead
    *     just be copied as-is, unchanged.
    *
    * <dt><code>"try"</code>
    * <dd>Attempt to process the file, but if that fails, then just copy the
    *     original file instead.
    * </dl>
    */
   private String _process;

   
   //-------------------------------------------------------------------------
   // Methods
   //-------------------------------------------------------------------------

   /**
    * Sets the path to the source directory. This parameter is required.
    *
    * @param dir
    *    the location of the source directory, or <code>null</code>.
    */
   public void setDir(File dir) {
      log("Setting \"dir\" to: " + quote(dir) + '.', MSG_VERBOSE);
      _sourceDir = dir;
   }

   /**
    * Sets the path to the destination directory. The default is the same
    * directory.
    *
    * @param dir
    *    the location of the destination directory, or <code>null</code>.
    */
   public void setToDir(File dir) {
      log("Setting \"toDir\" to: " + quote(dir) + '.', MSG_VERBOSE);
      _destDir = dir;
   }

   /**
    * Sets the command to execute, optionally. By default this task will find
    * a proper command on the current path.
    *
    * @param command
    *    the command to use, e.g. <code>"/usr/local/bin/optipng"</code>,
    *    can be <code>null</code> (in which case the task will find the command).
    */
   public void setCommand(String command) {
      log("Setting \"command\" to: " + quote(command) + '.', MSG_VERBOSE);
      _command = command;
   }

   /**
    * Configures the time-out for executing a single OptiPNG command. The
    * default is 60 seconds. Setting this to 0 or lower disables the time-out
    * completely.
    *
    * @param timeOut
    *    the time-out to use in milliseconds, or 0 (or lower) if no time-out
    *    should be applied.
    */
   public void setTimeOut(long timeOut) {
      log("Setting \"timeOut\" to: " + timeOut + " ms.", MSG_VERBOSE);
      _timeOut = timeOut;
   }

   /**
    * Sets whether the files should be processed with OptiPNG at all.
    * There are 3 options:
    * <dl>
    * <dt><code>"yes"</code> or <code>"true"</code>
    * <dd>The files <em>must</em> be processed by OptiPNG.
    *     If OptiPNG is unavailable, then this task fails.
    *
    * <dt><code>"no"</code> or <code>"false"</code>
    * <dd>The files must <em>not</em> be processed by OptiPNG but must instead
    *     just be copied as-is, unchanged.
    *
    * <dt><code>"try"</code>
    * <dd>If OptiPNG is available process the file(s), but if OptiPNG is
    *     unavailable, then just copy the files instead (in which case a
    *     warning will be output).
    * </dl>
    *
    * @param s
    *    the value, should be one of the allowed values (otherwise the task
    *    will fail during execution).
    */
   public void setProcess(String s) {
      log("Setting \"process\" to: " + quote(s) + '.', MSG_VERBOSE);
      _process = s;
   }

   @Override
   public void execute() throws BuildException {

      // Source directory defaults to current directory
      if (_sourceDir == null) {
         _sourceDir = getProject().getBaseDir();
      }

      // Destination directory defaults to source directory
      if (_destDir == null) {
         _destDir = _sourceDir;
      }

      // Check the directories
      checkDir("Source directory",      _sourceDir,  true, false);
      checkDir("Destination directory",   _destDir, false,  true);

      // Interpret the "process" option
      ProcessOption processOption;
      String p = (_process == null) ? null : _process.toLowerCase().trim();
      if (p == null || "true".equals(p) || "yes".equals(p)) {
         processOption = ProcessOption.MUST;
      } else if ("false".equals(_process) || "no".equals(_process)) {
         processOption = ProcessOption.MUST_NOT;
      } else if ("try".equals(_process)) {
         processOption = ProcessOption.SHOULD;
      } else {
         throw new BuildException("Invalid value for \"process\" option: " + quote(_process) + '.');
      }

      // Determine what command to execute
      String command = (_command == null || _command.length() < 1)
                     ? DEFAULT_COMMAND
                     : _command;

      // Test that the command is available
      boolean commandAvailable = testCommand(command, processOption);

      // Determine if transformation should be attempted at all
      // (alternative is just copying)
      boolean transform = processOption != ProcessOption.MUST_NOT && commandAvailable;

      // Consider each individual file for processing/copying
      log("Transforming from " + _sourceDir.getPath() + " to " + _destDir.getPath() + '.', MSG_VERBOSE);
      long start = System.currentTimeMillis();
      int failedCount = 0, optimizeCount = 0, copyCount = 0, skippedCount = 0;
      for (String inFileName : getDirectoryScanner(_sourceDir).getIncludedFiles()) {

         // Make sure the input file exists
         File inFile = new File(_sourceDir, inFileName);
         if (! inFile.exists()) {
            continue;
         }

         // Determine if the file type is supported
         if (! matches(inFileName.toLowerCase(), "\\.(gif|bmp|png|pnm|tif|tiff)$")) {
            log("Skipping " + quote(inFileName) + " because the file type is unsupported.", MSG_VERBOSE);
            skippedCount++;
            continue;
         }

         // Some preparations related to the input file and output file
         long     thisStart = System.currentTimeMillis();
         String outFileName = inFileName.replaceFirst("\\.[a-zA-Z]+$", ".png");
         File       outFile = new File(_destDir, outFileName);
         String outFilePath = outFile.getPath();
         String  inFilePath = inFile.getPath();

         // Skip this file is the output file exists and is newer
         if (outFile.exists() && (outFile.lastModified() > inFile.lastModified())) {
            log("Skipping " + quote(inFileName) + " because output file is newer.", MSG_VERBOSE); 
            skippedCount++;
            continue;

         // Skip each empty file
         } else if (inFile.length() < 1L) {
            log("Skipping " + quote(inFileName) + " because the file is completely empty.", MSG_VERBOSE); 
            skippedCount++;
            continue;
         }

         // File transformation (optimization) should be attempted
         boolean copy = !transform;
         if (transform) {

            // Prepare for the command execution
            Buffer            buffer = new Buffer();
            ExecuteWatchdog watchdog = (_timeOut > 0L) ? new ExecuteWatchdog(_timeOut) : null;
            Execute          execute = new Execute(buffer, watchdog);
            String[]         cmdline = new String[] { command, "-fix", "-force", "-out", outFilePath, "--", inFilePath };

            execute.setAntRun(getProject());
            execute.setCommandline(cmdline);

            // Execute the command
            boolean failure;
            try {
               execute.execute();
               failure = execute.isFailure();
            } catch (IOException cause) {
               failure = true;
            }

            // Output to stderr indicates a failure
            String errorOutput = buffer.getErrString();
            failure            = failure ? true : ! isEmpty(errorOutput);

            // A non-existent or empty file also indicate failure
            if (! failure) {
               if (! outFile.exists()) {
                  failure     = true;
                  errorOutput = "Output file not created.";
               } else if (outFile.length() < 1L) {
                  failure     = true;
                  errorOutput = "Generated output file is empty.";
               }
            }

            // Log the result for this individual file
            long thisDuration = System.currentTimeMillis() - thisStart;
            if (failure) {
               String logMessage = "Failed to optimize " + quote(inFilePath);
               if (isEmpty(errorOutput)) {
                  logMessage += '.';
               } else {
                  logMessage += ": " + errorOutput;
               }
               log(logMessage, MSG_ERR);
               failedCount++;

               // Failed, but then instead copy the input file unchanged
               if (processOption != ProcessOption.MUST) {
                  copy = true;
               }
            } else {
               log("Optimized " + quote(inFileName) + " in " + thisDuration + " ms.", MSG_VERBOSE);
               optimizeCount++;
            }
         }

         // Copy the file?
         if (copy) {
            try {
               FileUtils.getFileUtils().copyFile(inFile, outFile);
               long thisDuration = System.currentTimeMillis() - thisStart;
               log("Copied " + quote(inFileName) + " in " + thisDuration + " ms.", MSG_VERBOSE);
               copyCount++;
            } catch (Throwable cause) {
               String logMessage = "Failed to copy " + quote(inFilePath) + " to " + quote(outFilePath) + '.';
               log(logMessage, MSG_ERR);
               failedCount++;
            }
         }
      }

      // Log the total result
      long duration = System.currentTimeMillis() - start;
      if (failedCount > 0) {
         throw new BuildException("" + failedCount + " file(s) failed to be optimized and/or copied; " + optimizeCount + " file(s) optimized; " + copyCount + " file(s) copied; " + skippedCount + " file(s) skipped. Total duration is " + duration + " ms.");
      } else {
         log("" + optimizeCount + " file(s) optimized and " + copyCount + " file(s) copied in " + duration + " ms; " + skippedCount + " file(s) skipped.");
      }
   }

   private boolean testCommand(String command, ProcessOption processOption)
   throws IllegalArgumentException, BuildException {

      // Check preconditions
      if (command == null) {
         throw new IllegalArgumentException("command == null");
      } else if (processOption == null) {
         throw new IllegalArgumentException("processOption == null");
      }

      // Short-circuit if no command should be executed
      if (processOption == ProcessOption.MUST_NOT) {
         return false;
      }

      // Create a watch dog, if a time-out is configured
      ExecuteWatchdog watchdog = (_timeOut > 0L) ? new ExecuteWatchdog(_timeOut) : null;

      // Check that the command is executable
      Buffer    buffer = new Buffer();
      Execute  execute = new Execute(buffer, watchdog);
      String[] cmdline = new String[] { command, "-version" };
      execute.setAntRun(getProject());
      execute.setCommandline(cmdline);
      Throwable caught;
      try {
         execute.execute();
         caught = null;
      } catch (Throwable e) {
         caught = e;
      }

      // Executing the command triggered an exception
      boolean commandAvailable;
      if (caught != null) {
         String message = "Unable to execute OptiPNG command " + quote(command) + '.';
         if (processOption == ProcessOption.MUST) {
            throw new BuildException(message, caught);
         } else {
            log(message, MSG_ERR);
            commandAvailable = false;
         }

      // Executing the command resulted in a non-zero code, indicating failure
      } else if (execute.getExitValue() != 0) {
         String message = "Unable to execute OptiPNG command " + quote(command) + ". Running '" + command + " -v' resulted in exit code " + execute.getExitValue() + '.';
         if (processOption == ProcessOption.MUST) {
            throw new BuildException(message);
         } else {
            log(message, MSG_ERR);
            commandAvailable = false;
         }

      // Command was executed successfully
      } else {
         Pattern pattern = Pattern.compile("^[^0-9]*([0-9]+(\\.[0-9]+)*)");
         Matcher matcher = pattern.matcher(buffer.getOutString());
         String  version = matcher.find() ? quote(matcher.group(1)) : "unknown";
         log("Using command " + quote(command) + ", version is " + version + '.', MSG_VERBOSE);
         commandAvailable = true;
      }

      return commandAvailable;
   }


   //-------------------------------------------------------------------------
   // Inner classes
   //-------------------------------------------------------------------------

   /**
    * Enumeration type for the different process options.
    *
    * @author <a href="mailto:ernst@pensioenpage.com">Ernst de Haan</a>
    */
   private enum ProcessOption {

      /**
       * Force processing with OptiPNG. If the OptiPNG command is not
       * available, then fail.
       */
      MUST,
         
      /**
       * Skip OptiPNG processing completely. Just copy the files.
       */
      MUST_NOT,

      /**
       * Try OptiPNG processing. If the processing fails, then copy the
       * original file.
       */
      SHOULD;
   }
}
