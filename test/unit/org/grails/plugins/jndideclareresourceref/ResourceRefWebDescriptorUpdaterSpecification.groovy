package org.grails.plugins.jndideclareresourceref

import spock.lang.Specification

class ResourceRefWebDescriptorUpdaterSpecification extends Specification {
  static final String WEB_XML = """\
<web-app xmlns="http://java.sun.com/xml/ns/javaee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         metadata-complete="true" version="2.5"
         xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd">

  <display-name>/some display name</display-name>

  <servlet>
    <servlet-name>grails</servlet-name>
    <servlet-class>org.codehaus.groovy.grails.web.servlet.GrailsDispatcherServlet</servlet-class>
  </servlet>
</web-app>
"""

  def "should not update web.xml when JNDI names are not available in config"() {
    given:
    ConfigObject configObject = new ConfigSlurper().parse("")
    def webXml = new XmlSlurper().parseText(WEB_XML)

    when:
    ResourceRefWebDescriptorUpdater.updateWebDescriptor(webXml, configObject)

    then:
    def webXmlResult = ResourceRefWebDescriptorUpdater.serializeWebXml(webXml)
    !webXmlResult.contains("resource-ref")
    !webXmlResult.contains("resource-env-ref")
  }

  def "should update web.xml when JNDI name for datasource is available in config"() {
    given:
    ConfigObject configObject = new ConfigSlurper().parse(
        """\
        dataSource.jndiName = 'java:comp/env/jdbc/dataSource'
        """.stripIndent()
    )
    def webXml = new XmlSlurper().parseText(WEB_XML)

    when:
    ResourceRefWebDescriptorUpdater.updateWebDescriptor(webXml, configObject)

    then:

    def resourceRef =  new XmlSlurper().parseText(ResourceRefWebDescriptorUpdater.serializeWebXml(webXml))."resource-ref"
    resourceRef.size() == 1
    resourceRef."res-ref-name".text() == "jdbc/dataSource"
    resourceRef."res-type".text() == "javax.sql.DataSource"
    resourceRef."res-auth".text() == "Container"
  }

  def "should update web.xml when multiple JNDI names for datasources is available in config"() {
    given:
    ConfigObject configObject = new ConfigSlurper().parse(
        """\
        dataSource.jndiName = 'java:comp/env/jdbc/dataSource'
        dataSource_second.jndiName = 'java:comp/env/jdbc/dataSourceSecond'
        """.stripIndent()
    )
    def webXml = new XmlSlurper().parseText(WEB_XML)

    when:
    ResourceRefWebDescriptorUpdater.updateWebDescriptor(webXml, configObject)

    then:
    def resourceRefCollection =  new XmlSlurper().parseText(ResourceRefWebDescriptorUpdater.serializeWebXml(webXml))."resource-ref"
    resourceRefCollection.size() == 2

    def dataSourceResourceRef = resourceRefCollection.find({ it."res-ref-name".text() == "jdbc/dataSource"})
    dataSourceResourceRef."res-ref-name".text() == "jdbc/dataSource"
    dataSourceResourceRef."res-type".text() == "javax.sql.DataSource"
    dataSourceResourceRef."res-auth".text() == "Container"

    def dataSourceSecondResourceRef = resourceRefCollection.find({ it."res-ref-name".text() == "jdbc/dataSourceSecond"})
    dataSourceSecondResourceRef."res-ref-name".text() == "jdbc/dataSourceSecond"
    dataSourceSecondResourceRef."res-type".text() == "javax.sql.DataSource"
    dataSourceSecondResourceRef."res-auth".text() == "Container"
  }

  def "datasources with same jndiName should be ignored"() {
    given:
    ConfigObject configObject = new ConfigSlurper().parse(
        """\
        dataSource.jndiName = 'java:comp/env/jdbc/dataSource'
        dataSource_second.jndiName = 'java:comp/env/jdbc/dataSource'
        """.stripIndent()
    )
    def webXml = new XmlSlurper().parseText(WEB_XML)

    when:
    ResourceRefWebDescriptorUpdater.updateWebDescriptor(webXml, configObject)

    then:
    def resourceRefCollection =  new XmlSlurper().parseText(ResourceRefWebDescriptorUpdater.serializeWebXml(webXml))."resource-ref"
    resourceRefCollection.size() == 1

    def dataSourceResourceRef = resourceRefCollection.find({ it."res-ref-name".text() == "jdbc/dataSource"})
    dataSourceResourceRef."res-ref-name".text() == "jdbc/dataSource"
    dataSourceResourceRef."res-type".text() == "javax.sql.DataSource"
    dataSourceResourceRef."res-auth".text() == "Container"
  }

  def "datasources with same res-ref-name as already present in web.xml should be ignored"() {
    given:
    ConfigObject configObject = new ConfigSlurper().parse(
        """\
        dataSource.jndiName = 'java:comp/env/jdbc/dataSource'
        dataSource_second.jndiName = 'java:comp/env/jdbc/dataSourceSecond'
        """.stripIndent()
    )


    def webXml = new XmlSlurper().parseText(
        """\
        <web-app xmlns="http://java.sun.com/xml/ns/javaee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 metadata-complete="true" version="2.5"
                 xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd">

          <display-name>/some display name</display-name>

          <servlet>
            <servlet-name>grails</servlet-name>
            <servlet-class>org.codehaus.groovy.grails.web.servlet.GrailsDispatcherServlet</servlet-class>
          </servlet>

          <resource-ref>
            <res-ref-name>jdbc/dataSource</res-ref-name>
            <description>some description</description>
          </resource-ref>
        </web-app>
        """.stripIndent())

    when:
    ResourceRefWebDescriptorUpdater.updateWebDescriptor(webXml, configObject)

    then:
    def resourceRefCollection =  new XmlSlurper().parseText(ResourceRefWebDescriptorUpdater.serializeWebXml(webXml))."resource-ref"
    resourceRefCollection.size() == 2

    def dataSourceResourceRef = resourceRefCollection.find({ it."res-ref-name".text() == "jdbc/dataSource"})
    dataSourceResourceRef."res-ref-name".text() == "jdbc/dataSource"
    dataSourceResourceRef."res-type".text() == ""
    dataSourceResourceRef."res-auth".text() == ""
    dataSourceResourceRef."description".text() == "some description"

    def dataSourceSecondResourceRef = resourceRefCollection.find({ it."res-ref-name".text() == "jdbc/dataSourceSecond"})
    dataSourceSecondResourceRef."res-ref-name".text() == "jdbc/dataSourceSecond"
    dataSourceSecondResourceRef."res-type".text() == "javax.sql.DataSource"
    dataSourceSecondResourceRef."res-auth".text() == "Container"
  }

  def "should update web.xml when JNDI name for mail session is available in config"() {
    given:
    ConfigObject configObject = new ConfigSlurper().parse(
        """\
        grails.mail.jndiName = 'java:comp/env/mail/mailSession'
        """.stripIndent()
    )
    def webXml = new XmlSlurper().parseText(WEB_XML)

    when:
    ResourceRefWebDescriptorUpdater.updateWebDescriptor(webXml, configObject)

    then:

    def resourceRef =  new XmlSlurper().parseText(ResourceRefWebDescriptorUpdater.serializeWebXml(webXml))."resource-ref"
    resourceRef.size() == 1
    resourceRef."res-ref-name".text() == "mail/mailSession"
    resourceRef."res-type".text() == "javax.mail.Session"
    resourceRef."res-auth".text() == "Container"
  }

  def "mail session with jndiName that already exist should be ignored"() {
    given:
    ConfigObject configObject = new ConfigSlurper().parse(
        """\
        dataSource.jndiName = 'java:comp/env/jdbc/dataSource'
        grails.mail.jndiName = 'java:comp/env/jdbc/dataSource'
        """.stripIndent()
    )
    def webXml = new XmlSlurper().parseText(WEB_XML)

    when:
    ResourceRefWebDescriptorUpdater.updateWebDescriptor(webXml, configObject)

    then:
    def resourceRefCollection =  new XmlSlurper().parseText(ResourceRefWebDescriptorUpdater.serializeWebXml(webXml))."resource-ref"
    resourceRefCollection.size() == 1

    def dataSourceResourceRef = resourceRefCollection.find({ it."res-ref-name".text() == "jdbc/dataSource"})
    dataSourceResourceRef."res-ref-name".text() == "jdbc/dataSource"
    dataSourceResourceRef."res-type".text() == "javax.sql.DataSource"
    dataSourceResourceRef."res-auth".text() == "Container"
  }

  def "should update web.xml with correct custom resource-ref config"() {
    given:
    ConfigObject configObject = new ConfigSlurper().parse(
        """\
        grails.jndiDeclareResourceRef.resourceRefList = [
            [resRefName: 'mail/mailSession', resType: 'javax.mail.Session', resAuth:'Container', resSharingScope: 'Unshareable', description: "some description"],
        ]
        """.stripIndent()
    )
    def webXml = new XmlSlurper().parseText(WEB_XML)

    when:
    ResourceRefWebDescriptorUpdater.updateWebDescriptor(webXml, configObject)

    then:
    def resourceRefCollection =  new XmlSlurper().parseText(ResourceRefWebDescriptorUpdater.serializeWebXml(webXml))."resource-ref"
    resourceRefCollection.size() == 1

    def resourceRef = resourceRefCollection.find({ it."res-ref-name".text() == "mail/mailSession"})
    resourceRef."res-ref-name".text() == "mail/mailSession"
    resourceRef."res-type".text() == "javax.mail.Session"
    resourceRef."res-auth".text() == "Container"
    resourceRef."res-sharing-scope".text() == "Unshareable"
    resourceRef."description".text() == "some description"
  }

  def "should not update web.xml with invalid custom resource-ref config"() {
    given:
    ConfigObject configObject = new ConfigSlurper().parse(
        """\
        grails.jndiDeclareResourceRef.resourceRefList = [
            [resRefName: 'mail/mailSession1', resType: 'javax.mail.Session', resAuth:'Container', resSharingScope: 'Unshareable', description: "some description"],
            [resRefName: 'mail/mailSession2', resAuth:'Container', resSharingScope: 'Unshareable', description: "some description"],
            [resRefName: 'mail/mailSession3', resType: 'javax.mail.Session', resSharingScope: 'Unshareable', description: "some description"],
            [resType: 'javax.mail.Session', resAuth:'Container', resSharingScope: 'Unshareable', description: "some description"]
        ]
        """.stripIndent()
    )
    def webXml = new XmlSlurper().parseText(WEB_XML)

    when:
    ResourceRefWebDescriptorUpdater.updateWebDescriptor(webXml, configObject)

    then:
    def resourceRefCollection =  new XmlSlurper().parseText(ResourceRefWebDescriptorUpdater.serializeWebXml(webXml))."resource-ref"
    resourceRefCollection.size() == 1

    def resourceRef = resourceRefCollection.find({ it."res-ref-name".text() == "mail/mailSession1"})
    resourceRef."res-ref-name".text() == "mail/mailSession1"
    resourceRef."res-type".text() == "javax.mail.Session"
    resourceRef."res-auth".text() == "Container"
    resourceRef."res-sharing-scope".text() == "Unshareable"
    resourceRef."description".text() == "some description"
  }

  def "custom resource-ref config should override values of detected resource-refs"() {
    ConfigObject configObject = new ConfigSlurper().parse(
        """\
        grails.mail.jndiName = 'java:comp/env/mail/mailSession'

        grails.jndiDeclareResourceRef.resourceRefList = [
            [resRefName: 'mail/mailSession', resSharingScope: 'Unshareable', description: "some description"],
        ]
        """.stripIndent()
    )
    def webXml = new XmlSlurper().parseText(WEB_XML)

    when:
    ResourceRefWebDescriptorUpdater.updateWebDescriptor(webXml, configObject)

    then:
    def resourceRefCollection =  new XmlSlurper().parseText(ResourceRefWebDescriptorUpdater.serializeWebXml(webXml))."resource-ref"
    resourceRefCollection.size() == 1

    def resourceRef = resourceRefCollection.find({ it."res-ref-name".text() == "mail/mailSession"})
    resourceRef."res-ref-name".text() == "mail/mailSession"
    resourceRef."res-type".text() == "javax.mail.Session"
    resourceRef."res-auth".text() == "Container"
    resourceRef."res-sharing-scope".text() == "Unshareable"
    resourceRef."description".text() == "some description"
  }

  def "should update web.xml with correct custom resource-env-ref config"() {
    given:
    ConfigObject configObject = new ConfigSlurper().parse(
        """\
        grails.jndiDeclareResourceRef.resourceEnvRefList = [
            [resourceEnvRefName: 'jms/myQueue', resourceEnvRefType: 'javax.jms.Queue', description: 'some description'],
        ]
        """.stripIndent()
    )
    def webXml = new XmlSlurper().parseText(WEB_XML)

    when:
    ResourceRefWebDescriptorUpdater.updateWebDescriptor(webXml, configObject)

    then:
    def resourceEnvRefCollection =  new XmlSlurper().parseText(ResourceRefWebDescriptorUpdater.serializeWebXml(webXml))."resource-env-ref"
    resourceEnvRefCollection.size() == 1

    def resourceEnvRef = resourceEnvRefCollection.find({ it."resource-env-ref-name".text() == "jms/myQueue"})
    resourceEnvRef."resource-env-ref-name".text() == "jms/myQueue"
    resourceEnvRef."resource-env-ref-type".text() == "javax.jms.Queue"
    resourceEnvRef."description".text() == "some description"
  }

  def "should not update web.xml with invalid custom resource-env-ref config"() {
    given:
    ConfigObject configObject = new ConfigSlurper().parse(
        """\
        grails.jndiDeclareResourceRef.resourceEnvRefList = [
            [resourceEnvRefName: 'jms/myQueue', resourceEnvRefType: 'javax.jms.Queue', description: 'some description'],
            [resourceEnvRefName: 'jms/myQueue', resourceEnvRefType: 'javax.jms.Queue', description: 'some description'],
            [resourceEnvRefType: 'javax.jms.Queue', description: 'some description'],
            [resourceEnvRefName: 'jms/myQueue', description: 'some description']
        ]
        """.stripIndent()
    )
    def webXml = new XmlSlurper().parseText(WEB_XML)

    when:
    ResourceRefWebDescriptorUpdater.updateWebDescriptor(webXml, configObject)

    then:
    def resourceEnvRefCollection =  new XmlSlurper().parseText(ResourceRefWebDescriptorUpdater.serializeWebXml(webXml))."resource-env-ref"
    resourceEnvRefCollection.size() == 1

    def resourceEnvRef = resourceEnvRefCollection.find({ it."resource-env-ref-name".text() == "jms/myQueue"})
    resourceEnvRef."resource-env-ref-name".text() == "jms/myQueue"
    resourceEnvRef."resource-env-ref-type".text() == "javax.jms.Queue"
    resourceEnvRef."description".text() == "some description"
  }
}
