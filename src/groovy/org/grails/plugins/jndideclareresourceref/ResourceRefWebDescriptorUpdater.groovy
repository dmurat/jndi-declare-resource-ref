package org.grails.plugins.jndideclareresourceref

import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ResourceRefWebDescriptorUpdater {
  static final Logger LOGGER = LoggerFactory.getLogger(ResourceRefWebDescriptorUpdater);

  static void updateWebDescriptor(webXml, ConfigObject applicationConfig) {
    List resourceRefDescriptorList = []

    addDataSourceResourceRefDescriptors(resourceRefDescriptorList, applicationConfig)
    addMailSessionResourceRefDescriptors(resourceRefDescriptorList, applicationConfig)
    addCustomResourceRefDescriptors(resourceRefDescriptorList, applicationConfig)
    resourceRefDescriptorList = filterOutExistingResourceRefs(webXml, resourceRefDescriptorList)

    if (resourceRefDescriptorList) {
      //noinspection GrUnresolvedAccess,GroovyAssignabilityCheck
      def lastWebXmlNode = webXml.children()[webXml.children().size() - 1]

      resourceRefDescriptorList.reverseEach { Map resourceRefDescriptor ->
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info("resource-ref added in web.xml - resource-ref: ${resourceRefDescriptor.inspect()}")
        }

        lastWebXmlNode + {
          "resource-ref" {
            if (resourceRefDescriptor.description) {
              "description"(resourceRefDescriptor.description)
            }

            "res-ref-name"(resourceRefDescriptor.resRefName)
            "res-type"(resourceRefDescriptor.resType)
            "res-auth"(resourceRefDescriptor.resAuth)

            if (resourceRefDescriptor.resSharingScope) {
              "res-sharing-scope"(resourceRefDescriptor.resSharingScope)
            }
          }
        }
      }
    }

    List configuredResourceEnvRefDescriptorList = createConfiguredResourceEnvRefDescriptors(applicationConfig)
    if (configuredResourceEnvRefDescriptorList) {
      //noinspection GrUnresolvedAccess, GroovyAssignabilityCheck
      def lastWebXmlNode = webXml.children()[webXml.children().size() - 1]

      configuredResourceEnvRefDescriptorList.reverseEach { Map resourceEnvRefDescriptor ->
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info("resource-env-ref added in web.xml - resource-env-ref: ${resourceEnvRefDescriptor.inspect()}")
        }

        lastWebXmlNode + {
          "resource-env-ref" {
            if (resourceEnvRefDescriptor.description) {
              "description"(resourceEnvRefDescriptor.description)
            }

            "resource-env-ref-name"(resourceEnvRefDescriptor.resourceEnvRefName)
            "resource-env-ref-type"(resourceEnvRefDescriptor.resourceEnvRefType)
          }
        }
      }
    }

    if (LOGGER.isDebugEnabled()) {
      String serializedWebXml
      if (webXml.respondsTo("serialize")) {
        serializedWebXml = webXml.serialize()
      }
      else {
        serializedWebXml = serializeWebXml(webXml)
      }

      LOGGER.debug("Following is a full web.xml after adding detected and configured resource-ref and resource-env-ref entries:\n${ serializedWebXml }")
    }
  }

  static String serializeWebXml(xml) {
    def outputBuilder = new StreamingMarkupBuilder()

    Writable outputXml = outputBuilder.bind {
      mkp.declareNamespace('': 'http://java.sun.com/xml/ns/javaee')
      mkp.yield xml
    } as Writable

    return XmlUtil.serialize(outputXml)
  }

  static void addDataSourceResourceRefDescriptors(List resourceRefDescriptorList, ConfigObject applicationConfig) {
    Map datasourceConfigMap = applicationConfig.findAll {
      it.key =~ /^dataSource.*/
    }

    datasourceConfigMap.each { dataSourceName, datasourceConfigOptions ->
      def jndiNameConfig = datasourceConfigOptions?.jndiName
      if (jndiNameConfig) {
        String jndiName = jndiNameConfig as String
        String referenceName = jndiName.replace("java:comp/env/", "")

        Map existingResourceRefDescriptorFound = resourceRefDescriptorList.find({ Map listElement -> listElement.resRefName == referenceName }) as Map
        if (existingResourceRefDescriptorFound) {
          LOGGER.warn(
              "Duplicate JNDI resource reference name is detected while trying to add a resource reference for datasource: " +
              "[dataSourceName: $dataSourceName, jndiName: $jndiName, referenceName: $referenceName]. Corresponding web.xml resource-ref entry for datasource will not be added."
          )
        }
        else {
          resourceRefDescriptorList << [resRefName: referenceName, resType: "javax.sql.DataSource", resAuth: "Container"]
        }
      }
    }
  }

  static void addMailSessionResourceRefDescriptors(List resourceRefDescriptorList, ConfigObject applicationConfig) {
    def mailSessionJndiNameConfig = applicationConfig?.grails?.mail?.jndiName
    if (mailSessionJndiNameConfig) {
      String jndiName = mailSessionJndiNameConfig as String
      String referenceName = jndiName.replace("java:comp/env/", "")

      Map existingResourceRefDescriptorFound = resourceRefDescriptorList.find({ Map listElement -> listElement.resRefName == referenceName }) as Map
      if (existingResourceRefDescriptorFound) {
        LOGGER.warn(
            "Duplicate JNDI resource reference name is detected while trying to add a resource reference for mail session: [jndiName: $jndiName, referenceName: $referenceName]. " +
            "Corresponding web.xml resource-ref entry for mail session will not be added.")
      }
      else {
        resourceRefDescriptorList << [resRefName: referenceName, resType: "javax.mail.Session", resAuth: "Container"]
      }
    }
  }

  static void addCustomResourceRefDescriptors(List resourceRefDescriptorList, ConfigObject applicationConfig) {
    def resourceRefListConfig = applicationConfig?.grails?.jndiDeclareResourceRef?.resourceRefList
    if (resourceRefListConfig) {
      List configuredResourceRefDescriptorList = resourceRefListConfig as List

      configuredResourceRefDescriptorList.each { Map configuredResourceRefDescriptor ->
        String resRefName = configuredResourceRefDescriptor.resRefName
        if (!resRefName) {
          LOGGER.warn("Skipping resource-ref config since there is no mandatory resource reference name defined - resource-ref: ${configuredResourceRefDescriptor.inspect()}")
          return
        }

        Boolean isOverriding = false
        Map resourceRefDescriptorFound = resourceRefDescriptorList.find({ Map resourceRefDescriptor -> resourceRefDescriptor.resRefName == resRefName }) as Map
        if (resourceRefDescriptorFound) {
          isOverriding = true
        }

        String resType = configuredResourceRefDescriptor.resType
        if (!isOverriding && !resType) {
          LOGGER.warn("Skipping resource-ref config since there is no mandatory resource type defined - resource-ref: ${configuredResourceRefDescriptor.inspect()}")
          return
        }

        String resAuth = configuredResourceRefDescriptor.resAuth
        if (!isOverriding && !resAuth) {
          LOGGER.warn("Skipping resource-ref config since there is no mandatory resource auth defined - resource-ref: ${configuredResourceRefDescriptor.inspect()}")
          return
        }

        String description = configuredResourceRefDescriptor.description
        String resSharingScope = configuredResourceRefDescriptor.resSharingScope

        if (isOverriding) {
          if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Overriding resource ref '$resRefName' ...")
            LOGGER.info("    before override resource-ref: ${resourceRefDescriptorFound.inspect()}")
          }

          if (resType) {
            resourceRefDescriptorFound.resType = resType
          }

          if (resAuth) {
            resourceRefDescriptorFound.resAuth = resAuth
          }

          if (description) {
            resourceRefDescriptorFound.description = description
          }

          if (resSharingScope) {
            resourceRefDescriptorFound.resSharingScope = resSharingScope
          }

          if (LOGGER.isInfoEnabled()) {
            LOGGER.info("    after override resource-ref: ${resourceRefDescriptorFound.inspect()}")
          }
        }
        else {
          Map resourceRefDescriptorToAdd = [resRefName: resRefName]

          if (resType) {
            resourceRefDescriptorToAdd.resType = resType
          }

          if (resAuth) {
            resourceRefDescriptorToAdd.resAuth = resAuth
          }

          if (description) {
            resourceRefDescriptorToAdd.description = description
          }

          if (resSharingScope) {
            resourceRefDescriptorToAdd.resSharingScope = resSharingScope
          }

          resourceRefDescriptorList << resourceRefDescriptorToAdd
        }
      }
    }
  }

  static List filterOutExistingResourceRefs(webXml, List resourceRefDescriptorList) {
    List existingResourceRefDescriptorList = []

    webXml."resource-ref".each { resourceRefGPath ->
      Map resourceRefDescriptor = [:]

      if (resourceRefGPath?."res-ref-name"?.text()) {
        resourceRefDescriptor.resRefName = resourceRefGPath."res-ref-name".text()
      }

      if (resourceRefGPath?."res-type"?.text()) {
        resourceRefDescriptor.resType = resourceRefGPath."res-type".text()
      }

      if (resourceRefGPath?."res-auth"?.text()) {
        resourceRefDescriptor.resAuth = resourceRefGPath."res-auth".text()
      }

      if (resourceRefGPath?."description"?.text()) {
        resourceRefDescriptor.description = resourceRefGPath."description".text()
      }

      if (resourceRefGPath?."res-sharing-scope"?.text()) {
        resourceRefDescriptor.resSharingScope = resourceRefGPath."res-sharing-scope".text()
      }

      if (resourceRefDescriptor) {
        existingResourceRefDescriptorList << resourceRefDescriptor
      }
    }

    List filteredOutResourceRefDescriptorList = resourceRefDescriptorList.findAll { Map resourceRefDescriptor ->
      Map existingResourceRefDescriptorFound = existingResourceRefDescriptorList.find { Map existingResourceRefDescriptor ->
        existingResourceRefDescriptor.resRefName == resourceRefDescriptor.resRefName
      }

      if (existingResourceRefDescriptorFound) {
        LOGGER.warn(
            "While trying to add a resource reference, already existing (in web.xml) resource reference is detected: [res-ref-name: $resourceRefDescriptor.resRefName]. Therefore, nothing new will " +
            "be added in web.xml. If this is not OK, than check your web.xml and remove declaration of unwanted resource reference."
        )

        return false
      }
      else {
        return true
      }
    }

    return filteredOutResourceRefDescriptorList
  }

  static List createConfiguredResourceEnvRefDescriptors(ConfigObject applicationConfig) {
    List resourceEnvRefDescriptorList = []

    def resourceEnvRefListConfig = applicationConfig?.grails?.jndiDeclareResourceRef?.resourceEnvRefList
    if (resourceEnvRefListConfig) {
      List configuredResourceEnvRefDescriptorList = resourceEnvRefListConfig as List

      configuredResourceEnvRefDescriptorList.each { Map configuredResourceEnvRefDescriptor ->
        Map resourceEnvRefDescriptor = [:]
        String resourceEnvRefName = configuredResourceEnvRefDescriptor.resourceEnvRefName
        if (!resourceEnvRefName) {
          LOGGER.warn("Skipping resource-env-ref config since there is no mandatory resource-env-ref-name defined - resource-env-ref: ${configuredResourceEnvRefDescriptor.inspect()}")
          return
        }
        else {
          Map descriptorFound = resourceEnvRefDescriptorList.find { Map descriptor -> descriptor.resourceEnvRefName == resourceEnvRefName} as Map
          if (descriptorFound) {
            LOGGER.warn("Skipping configured resource-env-ref since already existing one is detected for name '$resourceEnvRefName'.")
            return
          }

          resourceEnvRefDescriptor.resourceEnvRefName = resourceEnvRefName
        }

        String resourceEnvRefType = configuredResourceEnvRefDescriptor.resourceEnvRefType
        if (!resourceEnvRefType) {
          LOGGER.warn("Skipping resource-env-ref config since there is no mandatory resource-env-ref-type defined - resource-env-ref: ${configuredResourceEnvRefDescriptor.inspect()}")
          return
        }
        else {
          resourceEnvRefDescriptor.resourceEnvRefType = resourceEnvRefType
        }

        String description = configuredResourceEnvRefDescriptor.description
        if (description) {
          resourceEnvRefDescriptor.description = description
        }

        resourceEnvRefDescriptorList << resourceEnvRefDescriptor
      }
    }

    return resourceEnvRefDescriptorList
  }
}
