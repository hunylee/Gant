//  Gant -- A Groovy way of scripting Ant tasks.
//
//  Copyright © 2008–2011, 2013, 2014  Russel Winder
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

package org.codehaus.gant.tests.bugs

import org.codehaus.gant.tests.GantTestCase

/**
 *  A test to ensure that Gant objects are garbage collected appropriately.
 *
 *  <p>Original idea for the test due to Peter Ledbrook.</p>
 *
 *  @author Russel Winder <russel@winder.org.uk>
 */
final class GANT_33_Test extends GantTestCase {
  private final buildScript =  '''
function = {-> }
target(main: 'simpleTest') {
  println('Main target executing...')
  function()
}
'''
  private final scriptTemplate = '''
import gant.Gant
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
def refQueue = new ReferenceQueue()
def phantomRefs = new HashSet()
output = [ ] // Must be in the binding.
Thread.startDaemon {
  while(true) {
    def obj = refQueue.remove()
    if(obj != null) {
      output << obj.toString()
      phantomRefs.remove(obj)
    }
  }
}
def buildScript = '__BUILDSCRIPT_PATH__'
def target = 'main'
def gant = __CREATE_GANT__
def refA = new PhantomReference(gant, refQueue)
phantomRefs << refA
output << refA.toString()
__LOAD_SCRIPT__
__PROCESS_TARGET__
System.gc()
gant = __CREATE_GANT__
def refB = new PhantomReference(gant, refQueue)
phantomRefs << refB
output << refB.toString()
__LOAD_SCRIPT__
__PROCESS_TARGET__
System.gc()
Thread.sleep(500) //  Give time for the reference queue monitor to report in.
'''
  private File buildScriptFile
  private fileNamePrefix =  'gant_'
private fileNameSuffix = '_GANT_33_Test'
  void setUp() {
    super.setUp()
    buildScriptFile = File.createTempFile(fileNamePrefix, fileNameSuffix)
    buildScriptFile.write(buildScript)
  }
  void tearDown() {
    buildScriptFile.delete()
    //  Must ensure that this cache directory is the cache directory as listed in gant.Gant.
    //
    //  This action succeeds locally and on Codehaus' Atlassian Bamboo without the quiet:true but fails on
    //  TravisCI. It is not clear where the cache directory is on TravisCI. However by not failing if the
    //  delete fails we putatively solve the problem.
    new AntBuilder().delete(quiet: true) {
      fileset(dir: [System.properties.'user.home', '.gant', 'cache'].join(System.properties.'file.separator'), includes: fileNamePrefix + '*' + fileNameSuffix + '*')
    }
  }
  //////////////////////////////////////////////////////////////////////////////////////////////
  //  On Windows the string returned by createTempFile must have \ reprocessed before being used for other
  //  purposes.
  //////////////////////////////////////////////////////////////////////////////////////////////
  void testCorrectCollection() {
    //  Creates two Gant instances, one of which should be garbage collected, so the result of execution is
    //  a list of 3 items, the addresses of the two created objects and the address of the collected object
    //  -- which should be the same as the address of the first created object.
    final binding = new Binding(output: '')
    final groovyShell = new GroovyShell(binding)
    groovyShell.evaluate (
                          scriptTemplate
                          .replace('__BUILDSCRIPT_PATH__', escapeWindowsPath(buildScriptFile.path))
                          .replace('__CREATE_GANT__', 'new Gant()')
                          .replace('__LOAD_SCRIPT__', 'gant.loadScript(new File(buildScript))')
                          .replace('__PROCESS_TARGET__', 'gant.processTargets(target)')
                         )
    assertEquals(3, binding.output.size())
    //  if there is a garbage collected object then it should be the one we expect.
    if (binding.output.size() > 2) { assertEquals(binding.output[0], binding.output[2]) }
  }
  void testNoCollection() {
    //  Creates two Gant instances neither of which are garbage collected.  This is showing the presence of the "memory leak".
    final binding = new Binding(output: '')
    final groovyShell = new GroovyShell(binding)
    System.err.println('testNoCollection:  This test succeeds incorrectly, it is showing the presence of the bug.')
    groovyShell.evaluate (
                          scriptTemplate
                          .replace('__BUILDSCRIPT_PATH__', escapeWindowsPath(buildScriptFile.path))
                          .replace('__CREATE_GANT__', 'new Gant()')
                          .replace('__LOAD_SCRIPT__', '')
                          .replace('__PROCESS_TARGET__', 'gant.processArgs([ "-f", new File(buildScript).absolutePath, "-c", target ] as String[])')
                         )
    assertEquals(2, binding.output.size())
  }
}
