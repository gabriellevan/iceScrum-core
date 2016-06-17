/*
 * Copyright (c) 2014 Kagilum SAS.
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
 *
 */
package org.icescrum.core.services

import org.hibernate.proxy.HibernateProxyHelper
import org.icescrum.core.domain.Activity
import org.icescrum.core.domain.User
import grails.util.GrailsNameUtils
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType

class ActivityService extends IceScrumEventPublisher {

    Activity addActivity(Object item, User poster, String code, String label, String field = null, String beforeValue = null, String afterValue = null) {
        if (item.id == null) {
            throw new RuntimeException("You must save the entity [${item}] before calling addActivity")
        }
        def itemClass = HibernateProxyHelper.getClassWithoutInitializingProxy(item)
        def itemType = GrailsNameUtils.getPropertyName(itemClass)
        def activity = new Activity(poster: poster, parentRef: item.id, parentType: itemType,
                                    code: code, label: label, field:field, beforeValue: beforeValue, afterValue: afterValue)
        activity.save()
        item.addToActivities(activity)
        publishSynchronousEvent(IceScrumEventType.CREATE, activity)
        return activity
    }

    void removeAllActivities(Object item) {
        if (item.activities) {
            def activitiesToDelete = []
            activitiesToDelete.addAll(item.activities)
            activitiesToDelete.each { activity ->
                item.removeFromActivities(activity)
                activity.delete()
            }
        }
    }
}
