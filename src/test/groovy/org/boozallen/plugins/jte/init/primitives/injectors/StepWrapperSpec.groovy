/*
   Copyright 2018 Booz Allen Hamilton
   Licensed under the Apache License, Version 2.0 (the "License")
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
       http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package org.boozallen.plugins.jte.init.primitives.injectors

import spock.lang.*
import org.junit.ClassRule
import org.jvnet.hudson.test.JenkinsRule
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.boozallen.plugins.jte.job.AdHocTemplateFlowDefinition
import hudson.model.Result
import org.boozallen.plugins.jte.init.governance.libs.TestLibraryProvider
import org.boozallen.plugins.jte.util.TestUtil

class StepWrapperSpec extends Specification{

    @Shared @ClassRule JenkinsRule jenkins = new JenkinsRule()

    /**
     * for performance, use a common jenkins and library source. 
     * individual tests will reference steps defined in this library
     */ 
    def setupSpec(){
        TestLibraryProvider libProvider = new TestLibraryProvider()
        libProvider.addStep("exampleLibrary", "callNoParam", """
        void call(){
            println "step ran"
        }
        """)
        libProvider.addStep("exampleLibrary", "callOneParam", """
        void call(x){
            println "x=\${x}"
        }
        """)
        libProvider.addStep("exampleLibrary", "callTwoParam", """
        void call(x, y){
            println "x=\${x}"
            println "y=\${y}"
        }
        """)
        libProvider.addStep("exampleLibrary", "someStep", """
        void someMethod(){
            println "step ran"
        }
        void someMethod(x){
            println "x=\${x}"
        }
        void someMethod(x,y){
            println "x=\${x}"
            println "y=\${y}"
        }
        """)
        libProvider.addStep("exampleLibrary", "testConfig", """
        void call(){
            println "x=\${config.x}"
        }
        """)
        libProvider.addStep("exampleLibrary", "usePipelineSteps", """
        void call(){
            node{ 
                sh "echo canyouhearmenow" 
            }
        }
        """)
        libProvider.addStep("exampleLibrary", "returnsSomething", """
        void call(){
            return "foo"
        }
        """)
        libProvider.addStep("hasHooks", "hookStep", """
        @BeforeStep
        void before(context){
            println "BeforeStep Hook" 
        }
        @AfterStep
        void after(context){
            println "AfterStep Hook" 
        }
        @Notify
        void notify(context){
            println "Notify Hook" 
        }
        """)
        libProvider.addStep("hasHooks", "theStep", """
        void call(){
            println "the actual step" 
        }
        """)
        libProvider.addGlobally()
    }

    def "steps invocable via call shorthand with no params"(){
        given:
        def run
        WorkflowJob job = TestUtil.createAdHoc(jenkins, 
            config: "libraries{ exampleLibrary }",
            template: "callNoParam()"
        )

        when: 
        run = job.scheduleBuild2(0).get()

        then:
        jenkins.assertBuildStatusSuccess(run)
        jenkins.assertLogContains("step ran", run)
    }

    def "steps invocable via call shorthand with one param"(){
        given:
        def run
        WorkflowJob job = TestUtil.createAdHoc(jenkins, 
            config: "libraries{ exampleLibrary }",
            template: 'callOneParam("foo")'
        )

        when: 
        run = job.scheduleBuild2(0).get()

        then:
        jenkins.assertBuildStatusSuccess(run)
        jenkins.assertLogContains("x=foo", run)
    }

    def "steps invocable via call shorthand with more than one param"(){
        given:
        def run 
        WorkflowJob job = TestUtil.createAdHoc(jenkins, 
            config: "libraries{ exampleLibrary }",
            template: 'callTwoParam("foo","bar")'
        )

        when: 
        run = job.scheduleBuild2(0).get()

        then:
        jenkins.assertBuildStatusSuccess(run)
        jenkins.assertLogContains("x=foo", run)
        jenkins.assertLogContains("y=bar", run)
    }

    def "steps can invoke non-call methods with no params"(){
        given:
        def run
        WorkflowJob job = TestUtil.createAdHoc(jenkins, 
            config: "libraries{ exampleLibrary }",
            template: "someStep.someMethod()"
        )

        when: 
        run = job.scheduleBuild2(0).get()

        then:
        jenkins.assertBuildStatusSuccess(run)
        jenkins.assertLogContains("step ran", run)
    }

    def "steps can invoke non-call methods with 1 param"(){
        given:
        def run 
        WorkflowJob job = TestUtil.createAdHoc(jenkins, 
            config: "libraries{ exampleLibrary }",
            template: 'someStep.someMethod("foo")'
        )

        when: 
        run = job.scheduleBuild2(0).get()

        then:
        jenkins.assertBuildStatusSuccess(run)
        jenkins.assertLogContains("x=foo", run)
    }

    def "steps can invoke non-call methods with more than 1 param"(){
        given:
        def run
        WorkflowJob job = TestUtil.createAdHoc(jenkins, 
            config: "libraries{ exampleLibrary }",
            template: 'someStep.someMethod("foo", "bar")'
        )

        when: 
        run = job.scheduleBuild2(0).get()

        then:
        jenkins.assertBuildStatusSuccess(run)
        jenkins.assertLogContains("x=foo", run)
        jenkins.assertLogContains("y=bar", run)
    }

    def "steps can access configuration via config variable"(){
        given:
        def run 
        WorkflowJob job = TestUtil.createAdHoc(jenkins, 
            config: """
            libraries{
                exampleLibrary{
                    x = "foo"
                }
            }
            """,
            template: 'testConfig()'
        )

        when: 
        run = job.scheduleBuild2(0).get()

        then:
        jenkins.assertBuildStatusSuccess(run)
        jenkins.assertLogContains("x=foo", run)
    }

    def "steps can invoke pipeline steps directly"(){
        given:
        def run
        WorkflowJob job = TestUtil.createAdHoc(jenkins, 
            config: "libraries{ exampleLibrary }",
            template: "usePipelineSteps()"
        )

        when: 
        run = job.scheduleBuild2(0).get()

        then:
        jenkins.assertBuildStatusSuccess(run)
        jenkins.assertLogContains("canyouhearmenow", run)
    }

    def "return step return result through StepWrapper"(){
        given:
        def run
        WorkflowJob job = TestUtil.createAdHoc(jenkins, 
            config: "libraries{ exampleLibrary }",
            template: """
            x = returnsSomething() 
            println "x=\${x}"
            """
        )

        when: 
        run = job.scheduleBuild2(0).get()

        then:
        jenkins.assertBuildStatusSuccess(run)
        jenkins.assertLogContains("x=foo", run)
    }

    def "step method not found throws TemplateException"(){
        given:
        def run
        WorkflowJob job = TestUtil.createAdHoc(jenkins, 
            config: "libraries{ exampleLibrary }",
            template: "callNoParam.nonExistent()"
        )

        when: 
        run = job.scheduleBuild2(0).get()

        then:
        jenkins.assertBuildStatus(Result.FAILURE, run)
        jenkins.assertLogContains("Step callNoParam from the library exampleLibrary does not have the method nonExistent()", run)
    }

    def "step override during initialization throws exception"(){
        given:
        def run
        WorkflowJob job = TestUtil.createAdHoc(jenkins, 
            config: """
            libraries{
                exampleLibrary
            }
            keywords{
                callNoParam = "oops" 
            }
            """,
            template: 'println "doesnt matter"'
        )

        when: 
        run = job.scheduleBuild2(0).get()

        then:
        jenkins.assertBuildStatus(Result.FAILURE, run)
    }

    def "step override post initialization throws exception"(){
        given:
        def run
        WorkflowJob job = TestUtil.createAdHoc(jenkins, 
            config: "libraries{ exampleLibrary }",
            template: 'callNoParam = "oops"'
        )

        when: 
        run = job.scheduleBuild2(0).get()

        then:
        jenkins.assertBuildStatus(Result.FAILURE, run)
    }

}