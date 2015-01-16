package org.silverpeas.setup.configuration

import org.gradle.api.tasks.TaskAction

/**
 * This task is to configure JBoss/Wildfly in order to be ready to run Silverpeas.
 * @author mmoquillon
 */
class JBossConfigurationTask extends AbstractConfigurationTask {
  def jboss = new JBossServer("${project.silverconf.jbossHome}")
      .redirectOutputTo(new File("${project.silverconf.logDir}/output.log"))

  JBossConfigurationTask() {
    description = 'Configure JBoss/Wildfly for Silverpeas'
    group = 'Build'
    dependsOn = ['assemble']
    outputs.upToDateWhen {
      return jboss.isAlreadyConfigured()
    }
  }

  @TaskAction
  def configureJBoss() {
    init()
    if (!jboss.isRunning()) {
      println 'JBoss not started, so start it'
      jboss.start()
    }

    String jbossConfFilesDir = "${project.buildDir}/cli"
    setUpJDBCDriver()
    installAdditionalModules()
    generateConfigurationFilesInto(jbossConfFilesDir)
    processConfigurationFiles(jbossConfFilesDir)
    println 'Stop JBoss now'
    jboss.stop()
  }

  private def installAdditionalModules() {
    project.copy {
      from "${project.silverconf.modulesDir}/jboss"
      into "${project.silverconf.jbossHome}/modules"
    }
  }

  private def generateConfigurationFilesInto(String jbossConfDir) {
    new File("${project.silverconf.configurationHome}/jboss").listFiles().each { cli ->
      String[] resource = cli.name.split('\\.')
      ResourceType type = ResourceType.valueOf(resource[1])
      println "Prepare configuration of ${type} ${resource[0]} for Silverpeas"
      project.copy {
        from(cli) {
          filter({ line ->
            return VariableReplacement.parseValue(line, settings)

          })
        }
        into jbossConfDir
      }
    }
  }

  private def setUpJDBCDriver() {
    println "Install datasource driver for ${settings.DB_SERVERTYPE}"
    project.copy {
      from "${project.silverconf.driversDir}/${settings.DB_DRIVER_NAME}"
      into "${project.silverconf.jbossHome}/standalone/deployments"
    }
  }

  private void processConfigurationFiles(jbossConfFilesDir) {
    new File(jbossConfFilesDir).listFiles().each { cli ->
      String[] resource = cli.name.split('\\.')
      ResourceType type = ResourceType.valueOf(resource[1])
      println "Configure ${type} ${resource[0]} for Silverpeas"
      jboss.processCommandFile(new File("${jbossConfFilesDir}/${cli.name}"),
          new File("${project.silverconf.logDir}/configuration-jboss.log"))
      println()
    }
  }

  private enum ResourceType {
    ra('resource adapter'),
    ds('datasource'),
    dl('deployment location'),
    sys('subsystem')

    private String type;

    protected ResourceType(String type) {
      this.type = type;
    }

    @Override
    String toString() {
      return type;
    }
  }
}