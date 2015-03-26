import org.grails.plugins.jndideclareresourceref.ResourceRefWebDescriptorUpdater

@SuppressWarnings("GroovyUnusedDeclaration")
class JndiDeclareResourceRefGrailsPlugin {
  def version = "1.0.0.BUILD-SNAPSHOT"
  def grailsVersion = "2.0 > *"

  def pluginExcludes = [
  ]

  def title = "Declare JNDI resource references" // Headline display name of the plugin
  def author = "Damir Murat"
  def authorEmail = "damir.murat@gmail.com"
  def description = '''\
Automatically creates web.xml resource-ref entries for JNDI datasources and mail sessions used in application.
It also allows manual declaration of custom resource-ref and resource-env-ref, as well as overriding declarations picked out automatically.
'''

  def documentation = "https://github.com/dmurat/jndi-declare-resource-ref/blob/master/README.md"
  def license = "APACHE"
  def organization = [name: "CROZ d.o.o.", url: "http://www.croz.net/"]

  def developers = [
      [name: "Franjo Žilić", email: "frenky666@gmail.com"],
      [name: "Zoran Regvart", email: "zregvart@croz.net"]
  ]

  def issueManagement = [ system: "github", url: "https://github.com/dmurat/jndi-declare-resource-ref/issues" ]
  def scm = [ url: 'https://github.com/dmurat/jndi-declare-resource-ref' ]

  def doWithWebDescriptor = { webXml ->
    ResourceRefWebDescriptorUpdater.updateWebDescriptor(webXml, application.config)
  }
}
