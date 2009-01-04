//  Gant -- A Groovy way of scripting Ant tasks.
//
//  Copyright © 2008-9 Russel Winder
//
//  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software distributed under the License is
//  distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
//  implied. See the License for the specific language governing permissions and limitations under the
//  License.

package org.codehaus.gant.ant.tests ;

import java.io.BufferedReader ;
import java.io.File ;
import java.io.IOException ;
import java.io.InputStream ;
import java.io.InputStreamReader ;

import java.util.ArrayList ;
import java.util.List ;

import junit.framework.TestCase ;

import org.apache.tools.ant.BuildException ;
import org.apache.tools.ant.Project ;
import org.apache.tools.ant.ProjectHelper ;

/**
 *  Unit tests for the Gant Ant task.  In order to test things appropriately this test must be initiated
 *  without any of the Groovy, Gant or related jars in the class path.  Also of course it must be a JUnit
 *  test with no connection to Groovy or Gant.
 *
 *  @author Russel Winder
 */
public class Gant_Test extends TestCase {
  private final String separator = System.getProperty ( "file.separator" ) ;
  private final boolean isWindows = System.getProperty ( "os.name" ).startsWith ( "Windows" ) ;
  private final String path ; {
    final StringBuilder sb = new StringBuilder ( ) ;
    sb.append ( "src" ) ;
    sb.append ( separator ) ;
    sb.append ( "test" ) ;
    sb.append ( separator ) ;
    sb.append ( "groovy" ) ;
    sb.append ( separator ) ;
    sb.append ( "org" ) ;
    sb.append ( separator ) ;
    sb.append ( "codehaus" ) ;
    sb.append ( separator ) ;
    sb.append ( "gant" ) ;
    sb.append ( separator ) ;
    sb.append ( "ant" ) ;
    sb.append ( separator ) ;
    sb.append ( "tests" ) ;
    path = sb.toString ( ) ;
  }
  private final File antFile = new File ( path , "gantTest.xml" ) ;
  private Project project ;

  //  This variable is assigned in the Gant script hence the public static.
  public static String returnValue ;

  @Override protected void setUp ( ) throws Exception {
    super.setUp ( ) ;
    project = new Project ( ) ;
    project.init ( ) ;
    ProjectHelper.getProjectHelper ( ).parse ( project , antFile ) ;
    returnValue = "" ;
  }

  public void testDefaultFileDefaultTarget ( ) {
    project.executeTarget ( "gantTestDefaultFileDefaultTarget" ) ;
    assertEquals ( "A test target in the default file." , returnValue ) ;
  }
  public void testDefaultFileNamedTarget ( ) {
    project.executeTarget ( "gantTestDefaultFileNamedTarget" ) ;
    assertEquals ( "Another target in the default file." , returnValue ) ;
  }
  public void testNamedFileDefaultTarget ( ) {
    project.executeTarget ( "gantTestNamedFileDefaultTarget" ) ;
    assertEquals ( "A test target in the default file." , returnValue ) ;
  }
  public void testNamedFileNamedTarget ( ) {
    project.executeTarget ( "gantTestNamedFileNamedTarget" ) ;
    assertEquals ( "Another target in the default file." , returnValue ) ;
  }
  public void testGantWithParametersAsNestedTags ( ) {
    project.executeTarget ( "gantWithParametersAsNestedTags" ) ;
    assertEquals ( "gant -Dflob=adob -Dburble gantParameters" , returnValue ) ;
  }
  public void testMultipleGantTargets ( ) {
    project.executeTarget ( "gantWithMultipleTargets" ) ;
    assertEquals ( "A test target in the default file.Another target in the default file." , returnValue ) ;
  }
  public void testUnknownTarget ( ) {
    try { project.executeTarget ( "blahBlahBlahBlah" ) ; }
    catch ( final BuildException be ) {
      assertEquals ( "Target \"blahBlahBlahBlah\" does not exist in the project \"Gant Ant Task Test\". " , be.getMessage ( ) ) ;
      return ;
    }
    fail ( "Should have got a BuildException." ) ;
  }
  public void testMissingGantfile ( ) {
    try { project.executeTarget ( "missingGantfile" ) ; }
    catch ( final BuildException be ) {
      assertEquals ( "Gantfile does not exist." , be.getMessage ( ) ) ;
      return ;
    }
    fail ( "Should have got a BuildException." ) ;
  }
  /*
   *  Test for the taskdef-related verify error problem.  Whatever it was supposed to do it passes now,
   *  2008-04-14.
   */
  public void testTaskdefVerifyError ( ) {
    project.executeTarget ( "gantTaskdefVerifyError" ) ;
    assertEquals ( "OK." , returnValue ) ;
  }
  /*
   *  A stream gobbler for the spawned process used by the <code>runAnt</code> method in the following
   *  tests.
   *
   *  @author Russel Winder
   */
  private static final class StreamGobbler extends Thread {
    private final InputStream is ;
    private final StringBuilder sb ;
    public StreamGobbler ( final InputStream is , final StringBuilder sb ) {
      this.is = is ;
      this.sb = sb ;
    }
    @Override public void run ( ) {
      try {
        final BufferedReader br = new BufferedReader ( new InputStreamReader ( is ) ) ;
        while ( true ) {
          final String line = br.readLine ( ) ;  //  Could throw an IOException hence the try block.
          if ( line == null ) { break ; }
          sb.append ( line ).append ( '\n' ) ;
        }
      }
      catch ( final IOException ioe ) { fail ( "Got an IOException reading a line in the read thread." ) ; }
    }
  }
  /*
   *  Run Ant in a separate process.  Return the string that results.
   *
   *  <p>This method assumes that either the environment variable ANT_HOME is set to a complete Ant
   *  installation or that the command ant (ant.bat on Windows) is in the path.</p>
   *
   *  <p>As at 2008-12-06 Canoo CruiseControl runs with GROOVY_HOME set to /usr/local/java/groovy, and
   *  Codehaus Bamboo runs without GROOVY_HOME being set.</p>
   *
   *  @param xmlFile the path to the XML file that Ant is to use.
   *  @param expectedReturnCode the return code that the Ant execution should return.
   *  @param withClasspath whether the Ant execution should use the full classpathso as to find all the classes.
   */
  private String runAnt ( final String xmlFile , final int expectedReturnCode , final boolean withClasspath ) {
    final List<String> command = new ArrayList<String> ( ) ;
    final String antHomeString = System.getenv ( "ANT_HOME" ) ;
    String antCommand ;
    if ( antHomeString != null ) { antCommand = antHomeString + separator + "bin" + separator  + "ant" ; }
    else { antCommand = "ant" ; }
    if ( isWindows ) {
      command.add ( "cmd.exe" ) ;
      command.add ( "/c" ) ;
      antCommand += ".bat" ;
    }
    command.add ( antCommand ) ;
    command.add ( "-f" ) ;
    command.add ( xmlFile ) ;
    if ( withClasspath ) {
      for ( final String p : System.getProperty ( "java.class.path" ).split ( System.getProperty ( "path.separator" ) ) ) {
        command.add ( "-lib" ) ;
        command.add ( p ) ;
      }
    }
    final ProcessBuilder pb = new ProcessBuilder ( command ) ;
    final StringBuilder outputStringBuilder = new StringBuilder ( ) ;
    final StringBuilder errorStringBuilder = new StringBuilder ( ) ;
    try {
      final Process p = pb.start ( ) ;  //  Could throw an IOException hence the try block.
      final Thread outputGobbler = new StreamGobbler ( p.getInputStream ( ) , outputStringBuilder ) ;
      final Thread errorGobbler = new StreamGobbler ( p.getErrorStream ( ) , errorStringBuilder ) ;
      outputGobbler.start ( ) ;
      errorGobbler.start ( ) ;
      try { assertEquals ( expectedReturnCode , p.waitFor ( ) ) ; }
      catch ( final InterruptedException ie ) { fail ( "Got an InterruptedException waiting for the Ant process to finish." ) ; }
      try { outputGobbler.join ( ) ;}
      catch ( final InterruptedException ie ) { fail ( "Got an InterruptedException waiting for the output gobbler to terminate." ) ; }
      try { errorGobbler.join ( ) ;}
      catch ( final InterruptedException ie ) { fail ( "Got an InterruptedException waiting for the error gobbler to terminate." ) ; }
      System.err.println ( errorStringBuilder ) ;
      return outputStringBuilder.toString ( ) ;
    }
    catch ( final Exception e ) { fail ( "Got a " +  e.getClass ( ).getName ( ) + " from starting the process." ) ; }
    //  Keep the compiler happy, it doesn't realize that execution cannot get here -- i.e. that fail is a non-returning function.
    return null ;
  }
  /*
   *  Tests stemming from GANT-19 and relating to ensuring the right classpath when loading the Groovyc Ant
   *  task.
   */
  private String createBaseMessage ( ) {
    final StringBuilder sb = new StringBuilder ( ) ;
    sb.append ( "Buildfile: " ) ;
    sb.append ( path ).append ( separator ) ;
    sb.append ( "gantTest.xml\n\n-initializeWithGroovyHome:\n\n-initializeNoGroovyHome:\n\ngantTestDefaultFileDefaultTarget:\n" ) ;
    return sb.toString ( ) ;
  }
  private String trimTimeFromSuccessfulBuild ( final String message ) {
    return message.replaceFirst ( "Total time: [0-9]*.*" , "" ) ;
  }
  public void testRunningAntFromShellFailsNoClasspath ( ) {
    //  On Windows the ant.bat file always returns zero :-(
    assertEquals ( createBaseMessage ( ) , runAnt ( path + separator + "gantTest.xml" , ( isWindows ? 0 : 1 ) , false ) ) ;
  }
  //
  //  TODO: Find out why this fails on Canoo CruiseControl even though it passes locally and on Codehaus
  //  Bamboo.  Data from builds 139--143 indicate that Ant is failing to executing anything at all.  This
  //  implies a configuration issue that is not true for the following test which also fails but ant does
  //  actually start.  This implies it is something to do with the path to the XML file that is a problem on
  //  Canoo Cruise Control but not on any other system.
  //
  public void testRunningAntFromShellSuccessful ( ) {
    assertEquals ( createBaseMessage ( ) + "\nBUILD SUCCESSFUL\n\n", trimTimeFromSuccessfulBuild ( runAnt ( path + separator + "gantTest.xml" , 0 , true ) ) ) ;
  }
  /*
   *  The following test is based on the code presented in email exchanges on the Groovy developer list by
   *  Chris Miles.  cf.  GANT-50.  This assumes that the tests are run from a directory other than this one.
   */
  //
  //  TODO: Find out why this test fails on Codehaus Bamboo and Canoo CruiseControl even though it passes
  //  locally.
  //
  //  On Canoo Cruise Control, ant starts but then fails in the build target.  It looks like an immediate
  //  fail so nothing in the build target is happening.  Could be a classpath problem for the Groovy ant
  //  task?
  //
  //  Codehaus Bamboo seems to not have any access to the detailed logs.
  //
  public void testBasedirInSubdir ( ) {
    final String pathToDirectory = System.getProperty ( "user.dir" )  + separator + path ;
    final StringBuilder sb = new StringBuilder ( ) ;
    sb.append ( "Buildfile: " ) ;
    sb.append ( path ).append ( separator ) ;
    sb.append ( "basedir.xml\n     [echo] basedir::ant basedir=" ) ;
    sb.append ( pathToDirectory ) ;
    sb.append ( "\n\n-initializeWithGroovyHome:\n\n-initializeNoGroovyHome:\n\nbuild:\n   [groovy] basedir::groovy basedir=" ) ;
    sb.append ( pathToDirectory ) ;
    sb.append ( "\n   [groovy] basedir::gant basedir=" ) ;
    //
    //  TODO:  this is wrong, it confirms the presence of the bug.
    //
    //sb.append ( pathToDirectory ) ;
    sb.append ( System.getProperty ( "user.dir" ) ) ;
    //
    sb.append ( "\n   [groovy] basedir::groovy basedir=" ) ;
    sb.append ( pathToDirectory ) ;
    //
    //  TODO : Why no groovy tag here?
    //
    // sb.append ( "\n   [groovy] basedir::gant basedir=" ) ;
    sb.append ( "\nbasedir::gant basedir=" ) ;
    //
    sb.append ( pathToDirectory ) ;
    //
    //  TODO : The <gant file="..."/> tag fails at the moment.
    //
    //sb.append ( "\n     [gant] basedir::gant basedir=" ) ;
    //sb.append ( pathToDirectory ) ;
    sb.append ( "\n\nBUILD SUCCESSFUL\n\n" ) ;
    assertEquals ( sb.toString ( ) , trimTimeFromSuccessfulBuild ( runAnt ( path + separator + "basedir.xml" , 0 , false ) ) ) ;
  }
}
