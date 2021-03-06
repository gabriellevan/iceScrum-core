/*
* Copyright (c) 2011 Kagilum SAS
*
* This file is part of iceScrum.
*
* iceScrum is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License.
*
* iceScrum is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
*
* Authors:
*
* Vincent Barrier (vbarrier@kagilum.com)
* Nicolas Noullet (nnoullet@kagilum.com)
*/


import com.quirklabs.hdimageutils.HdImageService
import grails.converters.JSON
import grails.plugins.wikitext.WikiTextTagLib
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.icescrum.core.cors.CorsFilter
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.event.IceScrumListener
import org.icescrum.core.utils.JSONIceScrumDomainClassMarshaller
import org.icescrum.plugins.attachmentable.domain.Attachment
import org.icescrum.plugins.attachmentable.services.AttachmentableService
import org.codehaus.groovy.grails.plugins.jasper.JasperReportDef
import org.codehaus.groovy.grails.plugins.jasper.JasperExportFormat
import org.springframework.web.servlet.support.RequestContextUtils as RCU
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.plugins.jasper.JasperService
import org.icescrum.core.support.ProgressSupport
import org.icescrum.core.services.UiDefinitionService
import org.icescrum.core.ui.UiDefinitionArtefactHandler
import org.codehaus.groovy.grails.commons.ControllerArtefactHandler
import org.icescrum.core.support.ApplicationSupport

import javax.servlet.http.HttpServletResponse
import java.lang.reflect.Method

class IcescrumCoreGrailsPlugin {
    def groupId = 'org.icescrum'
    // the plugin version
    def version = "1.7-SNAPSHOT"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.5 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]

    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    def artefacts = [new UiDefinitionArtefactHandler()]

    def watchedResources = [
        "file:./grails-app/conf/*UiDefinition.groovy",
        "file:./plugins/*/grails-app/conf/*UiDefinition.groovy"
    ]

    def observe = ['controllers']
    def loadAfter = ['controllers', 'feeds', 'hibernate']
    def loadBefore = ['autobase', 'grails-atmosphere-meteor']

    // TODO Fill in these fields
    def author = "iceScrum"
    def authorEmail = "contact@icescrum.org"
    def title = "iceScrum core plugin (include domain / services / taglib)"
    def description = '''
    iceScrum core plugin (include domain / services / taglib)
'''

    // URL to the plugin's documentation
    def documentation = "http://www.icescrum.org/plugin/icescrum-core"

    def doWithWebDescriptor = { xml ->
        if (application.config.icescrum.push.enable) {
            addAtmosphereSessionSupport(xml)
        }
        def cors = application.config.icescrum.cors
        if (cors.enable){
            addCorsSupport(xml, cors)
        }
    }

    def controllersWithDownloadAndPreview = ['story', 'actor', 'task', 'feature', 'sprint', 'release', 'project']

    def doWithSpring = {
        ApplicationSupport.createUUID()
        System.setProperty('lbdsl.home', "${application.config.icescrum.baseDir.toString()}${File.separator}lbdsl")
    }

    def doWithDynamicMethods = { ctx ->
        // Manually match the UIController classes
        SpringSecurityService springSecurityService = ctx.getBean('springSecurityService')
        HdImageService hdImageService = ctx.getBean('hdImageService')
        AttachmentableService attachmentableService = ctx.getBean('attachmentableService')
        JasperService jasperService = ctx.getBean('jasperService')
        UiDefinitionService uiDefinitionService = ctx.getBean('uiDefinitionService')
        uiDefinitionService.loadDefinitions()

        application.controllerClasses.each {
            addJasperMethod(it, springSecurityService, jasperService)
            if (it.logicalPropertyName in controllersWithDownloadAndPreview){
                addDownloadAndPreviewMethods(it, attachmentableService, hdImageService)
            }
        }

        application.serviceClasses.each {
            addListenerSupport(it, ctx)
        }
    }

    def doWithApplicationContext = { applicationContext ->
        //For iceScrum internal
        Map properties = application.config?.icescrum?.marshaller
        WikiTextTagLib textileRenderer = (WikiTextTagLib)application.mainContext["grails.plugins.wikitext.WikiTextTagLib"]
        JSON.registerObjectMarshaller(new JSONIceScrumDomainClassMarshaller(application, false, true, properties, textileRenderer), 1)
        applicationContext.bootStrapService.start()
    }

    def onChange = { event ->
        UiDefinitionService uiDefinitionService = event.ctx.getBean('uiDefinitionService')
        def type = UiDefinitionArtefactHandler.TYPE

        if (application.isArtefactOfType(type, event.source))
        {
            def oldClass = application.getArtefact(type, event.source.name)
            application.addArtefact(type, event.source)
            application.getArtefacts(type).each {
                if (it.clazz != event.source && oldClass.clazz.isAssignableFrom(it.clazz)) {
                    def newClass = application.classLoader.reloadClass(it.clazz.name)
                    application.addArtefact(type, newClass)
                }
            }
            uiDefinitionService.reload()
        }
        else if (application.isArtefactOfType(ControllerArtefactHandler.TYPE, event.source))
        {
            def controller = application.getControllerClass(event.source?.name)
            HdImageService hdImageService = event.ctx.getBean('hdImageService')
            AttachmentableService attachmentableService = event.ctx.getBean('attachmentableService')

            if(uiDefinitionService.hasWindowDefinition(controller.logicalPropertyName)) {
                if (controller.logicalPropertyName in controllersWithDownloadAndPreview){
                    addDownloadAndPreviewMethods(controller, attachmentableService, hdImageService)
                }
            }
            if (application.isControllerClass(event.source)) {
                SpringSecurityService springSecurityService = event.ctx.getBean('springSecurityService')
                JasperService jasperService = event.ctx.getBean('jasperService')
                addJasperMethod(event.source, springSecurityService, jasperService)
            }
        }
    }

    def onConfigChange = { event ->
        event.application.mainContext.uiDefinitionService.reload()
    }

    private addDownloadAndPreviewMethods(clazz, attachmentableService, hdImageService){
        def mc = clazz.metaClass
        def dynamicActions = [
            download : { ->
                Attachment attachment = Attachment.get(params.id as Long)
                if (attachment) {
                    if (attachment.url){
                        redirect(url: "${attachment.url}")
                        return
                    }else{
                        File file = attachmentableService.getFile(attachment)

                        if (file.exists()) {
                            if (!attachment.previewable){
                                String filename = attachment.filename
                                ['Content-disposition': "attachment;filename=\"$filename\"",'Cache-Control': 'private','Pragma': ''].each {k, v ->
                                    response.setHeader(k, v)
                                }
                            }
                            response.contentType = attachment.contentType
                            response.outputStream << file.newInputStream()
                            return
                        }
                    }
                }
                response.status = HttpServletResponse.SC_NOT_FOUND
            },
            preview: {
                Attachment attachment = Attachment.get(params.id as Long)
                File file = attachmentableService.getFile(attachment)
                def thumbnail = new File(file.parentFile.absolutePath+File.separator+attachment.id+'-thumbnail.'+(attachment.ext?.toLowerCase() != 'gif'? attachment.ext :'jpg'))
                if (!thumbnail.exists()){
                    thumbnail.setBytes(hdImageService.scale(file.absolutePath, 40, 40))
                }
                if (thumbnail.exists()){
                    response.contentType = attachment.contentType
                    response.outputStream << thumbnail.newInputStream()
                } else {
                    render (status: 404)
                }
            }
        ]

        dynamicActions.each{ actionName, actionClosure ->
            mc."${GrailsClassUtils.getGetterName(actionName)}" = {->
                actionClosure.delegate = delegate
                actionClosure.resolveStrategy = Closure.DELEGATE_FIRST
                actionClosure
            }
            clazz.registerMapping(actionName)
        }
    }

    private void addJasperMethod(source, springSecurityService, jasperService){
        try {
            source.metaClass.renderReport = { String reportName, String format, def data, String outputName = null, def parameters = null ->
                outputName = (outputName ? outputName.replaceAll("[^\\-a-zA-Z\\s]", "").replaceAll(" ", "") + '-' + reportName : reportName) + '-' + (g.formatDate(formatName: 'is.date.file'))
                if (!session.progress){
                     session.progress = new ProgressSupport()
                }
                session.progress.updateProgress(50, message(code: 'is.report.processing'))
                if (parameters){
                    parameters.SUBREPORT_DIR = "${servletContext.getRealPath('reports/subreports')}/"
                }else{
                    parameters = [SUBREPORT_DIR: "${servletContext.getRealPath('reports/subreports')}/"]
                }
                def reportDef = new JasperReportDef(name: reportName,
                                                    reportData: data,
                                                    locale: springSecurityService.isLoggedIn() ? springSecurityService.currentUser.locale : RCU.getLocale(request),
                                                    parameters: parameters,
                                                    fileFormat: JasperExportFormat.determineFileFormat(format))

                response.characterEncoding = "UTF-8"
                response.setHeader("Content-disposition", "attachment; filename=" + outputName + "." + reportDef.fileFormat.extension)
                session.progress?.completeProgress(message(code: 'is.report.complete'))
                render(file: jasperService.generateReport(reportDef).toByteArray(), contentType: reportDef.fileFormat.mimeTyp)
            }
        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            session.progress.progressError(message(code: 'is.report.error'))
        }
    }

    // Websockets are not backed by HttpSessions, which prevents from looking at the security context
    // One way to work around that consists in enabling HttpSession support at the atmosphere level
    // References:
    // - https://github.com/Atmosphere/atmosphere/wiki/Enabling-HttpSession-Support
    // - https://spring.io/blog/2014/09/16/preview-spring-security-websocket-support-sessions
    private addAtmosphereSessionSupport(xml) {
        def nodeName = 'listener' // An 1 level indirection is required, using the "listener" string literal fails miserably
        def listener = xml[nodeName]
        listener[listener.size() - 1] + {
            "$nodeName" {
                'listener-class'('org.atmosphere.cpr.SessionSupport')
            }
        }
        def contextParam = xml.'context-param'
        contextParam[contextParam.size() - 1] + {
            'context-param' {
                'param-name'('org.atmosphere.cpr.sessionSupport')
                'param-value'(true)
            }
        }
    }

    private addCorsSupport(def xml, def config){
        def contextParam = xml.'context-param'
        contextParam[contextParam.size() - 1] + {
            'filter' {
                'filter-name'('cors-headers')
                'filter-class'(CorsFilter.name)
                if (config.allow.origin.regex) {
                    'init-param' {
                        'param-name'('allow.origin.regex')
                        'param-value'(config.allow.origin.regex.toString())
                    }
                }
                if (config.headers instanceof Map) {
                    config.headers.each { k,v ->
                        'init-param' {
                            'param-name'('header:' + k)
                            'param-value'(v)
                        }
                    }
                }
                if (config.expose.headers) {
                    'init-param' {
                        'param-name'('expose.headers')
                        'param-value'(cors.expose.headers.toString())
                    }
                }
            }
        }

        def urlPattern = config.url.pattern ?: '/*'
        List list = urlPattern instanceof List ? urlPattern : [urlPattern]

        def filter = xml.'filter'
        list.each { pattern ->
            filter[0] + {
                'filter-mapping'{
                    'filter-name'('cors-headers')
                    'url-pattern'(pattern)
                }
            }
        }
    }

    private void addListenerSupport(serviceGrailsClass, ctx) {
        serviceGrailsClass.clazz.declaredMethods.each { Method method ->
            IceScrumListener listener = method.getAnnotation(IceScrumListener)
            if (listener) {
                def listenerService = ctx.getBean(serviceGrailsClass.propertyName)
                def domains =  listener.domain() ? [listener.domain()] : listener.domains()
                domains.each { domain ->
                    def publisherService = ctx.getBean(domain + 'Service')
                    if (publisherService && publisherService instanceof IceScrumEventPublisher) {
                        if (listener.eventType() == IceScrumEventType.UGLY_HACK_BECAUSE_ANNOTATION_CANT_BE_NULL) {
                            println 'Add listener on all ' + domain + ' events: ' + serviceGrailsClass.propertyName + '.' + method.name
                            publisherService.registerListener { eventType, object, dirtyProperties ->
                                listenerService."$method.name"(eventType, object, dirtyProperties)
                            }
                        } else {
                            println 'Add listener on ' + domain + ' ' + listener.eventType().toString() + ' events: ' + serviceGrailsClass.propertyName + '.' + method.name
                            publisherService.registerListener(listener.eventType()) { eventType, object, dirtyProperties ->
                                listenerService."$method.name"(object, dirtyProperties)
                            }
                        }
                    }
                }

            }
        }
    }
}
