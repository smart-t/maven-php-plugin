/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.phpmaven.lint.impl;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Configuration;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.phpmaven.core.BuildPluginConfiguration;
import org.phpmaven.core.ConfigurationParameter;
import org.phpmaven.core.IComponentFactory;
import org.phpmaven.exec.IPhpExecutable;
import org.phpmaven.exec.IPhpExecutableConfiguration;
import org.phpmaven.exec.PhpException;
import org.phpmaven.lint.ILintChecker;

/**
 * Lint checker runnable that will walk the queue and do lint checks.
 * 
 * @author mepeisen
 */
@Component(role = ILintChecker.class)
@BuildPluginConfiguration(groupId = "org.phpmaven", artifactId = "maven-php-validate-lint", filter = {"threads"})
public class LintThread implements Runnable {
    
    /**
     * The component factory.
     */
    @Requirement
    private IComponentFactory factory;
    
    /**
     * The maven session.
     */
    @ConfigurationParameter(name = "session", expression = "${session}")
    private MavenSession session;
    
    /**
     * The queue.
     */
    private LintQueue queue;
    
    /**
     * Logging.
     */
    private Log log;

    /**
     * Thread
     */
    private Thread thread;
    
    /**
     * Php config.
     */
    @Configuration(name = "executableConfig", value = "")
    private Xpp3Dom executableConfig;
    
    public Xpp3Dom getExecutableConfig() {
        return executableConfig;
    }

    public void setExecutableConfig(Xpp3Dom executableConfig) {
        this.executableConfig = executableConfig;
    }

    /**
     * Constructor.
     */
    public LintThread(LintQueue queue) {
        this.queue = queue;
    }

    /**
     * runs this thread.
     * @param log the log
     */
    public void run(Log log) {
        this.log = log;
        this.thread = new Thread(this);
        this.thread.start();
    }

    @Override
    public void run() {
        IPhpExecutable exec = null;
        try {
            final IPhpExecutableConfiguration config = this.factory.lookup(IPhpExecutableConfiguration.class, this.executableConfig, session);
            exec = config.getPhpExecutable(this.log);
        } catch (Exception ex) {
            this.log.error(ex);
            return;
        }
        while (!this.queue.isTerminated()) {
            final LintExecution execution = this.queue.pop();
            if (execution != null) {
                final String command = "-l \"" + execution.getFile().getAbsolutePath() + "\"";
                this.log.debug("Validating: " + execution.getFile().getAbsolutePath());
                try {
                    exec.execute(command, execution.getFile());
                } catch (PhpException e) {
                    execution.setException(e);
                    this.queue.addFailure(execution);
                }
            }
            else {
                this.queue.waitForQueue(50);
            }
        }
    }

    public void join() throws InterruptedException {
        this.thread.join();
    }
    
}