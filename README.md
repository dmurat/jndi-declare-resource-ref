# jndi-declare-resource-ref
Grails plugin which automatically creates `resource-ref` entries in web.xml for detected JNDI datasources and mail sessions.

## Installation and usage
Add the plugin as build dependency under plugins section in your BuildConfig.groovy.

    build ":jndi-declare-resource-ref:1.0.0.BUILD-SNAPSHOT"

This is all what is required if you need auto-created `resource-ref`s only for JNDI data sources and mail sessions (as used and configured by grails mail plugin). 

Created resource references will have only three sub elements, `res-ref-name`, `res-type` and `res-auth` elements, where `res-auth` always has a value of `Container`. For example, for a datasource 
whose JNDI name is declared like

    dataSource.jndiName = 'java:comp/env/jdbc/dataSource'

plugin will add following `resource-ref` in web.xml:

    <resource-ref>
      <res-ref-name>jdbc/dataSource</res-ref-name>
      <res-type>javax.sql.DataSource</res-type>
      <res-auth>Container</res-auth>
    </resource-ref>

## Manual configuration of resource-ref and resource-env-ref elements
Besides automatic resource-ref declaration, plugin supports a few more options which require manual configuration. For example, when there is a need to declare a custom `resource-ref`, which does not 
represent datasource or mail session, they can be declared in `Config.groovy` through `grails.jndiDeclareResourceRef.resourceRefList` config option:

    grails.jndiDeclareResourceRef.resourceRefList = [
        [resRefName: 'jms/myConnectionFactory', resType: 'javax.jms.QueueConnectionFactory', resAuth: 'Container', resSharingScope: 'Unshareable', description: "My queue connection factory"],
        [resRefName: 'jms/myOtherConnectionFactory', resType: 'javax.jms.QueueConnectionFactory', resAuth: 'Container']
    ]

Names of map keys correspond to names of `resource-ref` sub-elements, but are specified in camel case. Therefore, above configuration will produce following `resource-ref`s in web.xml:

    <resource-ref>
      <res-ref-name>jms/myConnectionFactory</res-ref-name>
      <res-type>javax.jms.QueueConnectionFactory</res-type>
      <res-auth>Container</res-auth>
      <res-sharing-scope>Unshareable<res-sharing-scope>
      <description>My queue connection factory</description>
    </resource-ref>
    
    <resource-ref>
      <res-ref-name>jms/myOtherConnectionFactory</res-ref-name>
      <res-type>javax.jms.QueueConnectionFactory</res-type>
      <res-auth>Container</res-auth>
    </resource-ref>

Manual configuration is also allowed for resource-env-ref elements through `grails.jndiDeclareResourceRef.resourceEnvRefList` config option:

    grails.jndiDeclareResourceRef.resourceEnvRefList = [
        [resourceEnvRefName: 'jms/myQueue', resourceEnvRefType: 'javax.jms.Queue', description: 'some description']
    ]

Configuration above will produce following `resource-env-ref` entry in web.xml:

    <resource-env-ref>
      <resource-env-ref-name>jms/myQueue</resource-env-ref-name>
      <resource-env-ref-type>javax.jms.Queue</resource-env-ref-type>
      <description>some description</description>
    </resource-env-ref>

## Overriding auto-created resource-ref elements
Config option `grails.jndiDeclareResourceRef.resourceRefList` can also be used for overriding values of automatically created `resource-ref`s for data sources and mail sessions. For example, suppose
that we have following config for JNDI mail session (as used by grails mail plugin):

    grails.mail.jndiName = 'java:comp/env/mail/mailSession'

This will produce following `resource-ref`:

    <resource-ref>
      <res-ref-name>mail/mailSession</res-ref-name>
      <res-type>javax.mail.Session</res-type>
      <res-auth>Container</res-auth>
    </resource-ref>

To override settings, or just to add description, resource-ref configuration with same resourceRefName can be specified like this: 

    grails.jndiDeclareResourceRef.resourceRefList = [
        [resRefName: 'mail/mailSession', description: "My mail session"]
    ]

## Logging
To see log output of a plugin, turn on desired level of logging for a package `org.grails.plugins.jndideclareresourceref`:

    debug "org.grails.plugins.jndideclareresourceref"
