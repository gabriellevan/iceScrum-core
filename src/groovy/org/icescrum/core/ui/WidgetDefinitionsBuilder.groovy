package org.icescrum.core.ui

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/*
 * Copyright (c) 2012 Kagilum SAS
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
 * Nicolas Noullet (nnoullet@kagilum.com)
 *
 */
class WidgetDefinitionsBuilder {

    private final log = LoggerFactory.getLogger(this.class.name)

    private boolean disabled = false
    private ConcurrentHashMap widgetsDefinitionsById

    WidgetDefinitionsBuilder(ConcurrentHashMap widgetsDefinitionsById, boolean disabled) {
        this.disabled = disabled
        this.widgetsDefinitionsById = widgetsDefinitionsById
    }

    def invokeMethod(String name, args) {
        if (args.size() == 1 && args[0] instanceof Closure) {
            def definitionClosure = args[0]
            WidgetDefinition widgetDefinition = new WidgetDefinition(name, disabled)
            definitionClosure.delegate = widgetDefinition
            definitionClosure.resolveStrategy = Closure.DELEGATE_FIRST
            definitionClosure()
            if(widgetsDefinitionsById[name]) {
                log.warn("UI widget definition for $name will be overriden")
            }
            widgetsDefinitionsById[name] = widgetDefinition
            if (log.debugEnabled) { log.debug("Added new UI widget definition for $name and status is : ${disabled ? 'disabled' : 'enabled'}") }
        }
    }
}