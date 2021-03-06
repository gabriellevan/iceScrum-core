/*
 * Copyright (c) 2015 Kagilum SAS
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
 * Manuarii Stein (manuarii.stein@icescrum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */


package org.icescrum.core.domain

import org.hibernate.ObjectNotFoundException
import org.icescrum.plugins.attachmentable.interfaces.Attachmentable

import java.sql.Timestamp


class Release extends TimeBox implements Cloneable, Attachmentable {

    static final int STATE_WAIT = 1
    static final int STATE_INPROGRESS = 2
    static final int STATE_DONE = 3

    int state = Release.STATE_WAIT
    String vision = "" // Beware of distinct, it won't work in MSSQL since this attribute is TEXT
    String name = "R"
    SortedSet<Sprint> sprints
    Date inProgressDate
    Date doneDate

    static belongsTo = [parentProduct: Product]

    static hasMany = [sprints: Sprint, features: Feature]

    static mappedBy = [sprints: 'parentRelease', features: 'parentRelease']

    static transients = ['firstDate', 'closable', 'activable', 'meanVelocity', 'previousRelease', 'nextRelease']

    static mapping = {
        cache true
        table 'icescrum2_release'
        vision type: 'text'
        name index: 'rel_name_index'
        sprints cascade: 'all-delete-orphan', cache: true
    }

    static constraints = {
        vision nullable: true
        inProgressDate nullable: true
        doneDate nullable: true
        name(blank: false, unique: 'parentProduct')
        startDate(validator: { val, obj ->
            if (val.before(obj.parentProduct.startDate)) {
                return ['before.productStartDate']
            }
            def r = obj.parentProduct.releases?.find { it.orderNumber == obj.orderNumber - 1 }
            if (r && val.before(r.endDate)) {
                return ['before.previous']
            }
            return true
        })
        state(validator: { val, obj ->
            if (val == STATE_DONE && obj.sprints.any { it.state != Sprint.STATE_DONE })
                return ['sprint.not.done']
            return true
        })
    }

    static namedQueries = {
        findCurrentOrNextRelease { p ->
            parentProduct {
                eq 'id', p
            }
            or {
                eq 'state', Release.STATE_INPROGRESS
                eq 'state', Release.STATE_WAIT
            }
            order("orderNumber", "asc")
            maxResults(1)
        }

        findCurrentOrLastRelease { p ->
            parentProduct {
                eq 'id', p
            }
            or {
                eq 'state', Release.STATE_INPROGRESS
                eq 'state', Release.STATE_DONE
            }
            order("orderNumber", "desc")
            maxResults(1)
        }

        getInProduct { p, id ->
            parentProduct {
                eq 'id', p
            }
            and {
                eq 'id', id
            }
            uniqueResult = true
        }
    }

    static Release withRelease(long productId, long id) {
        Release release = (Release) getInProduct(productId, id).list()
        if (!release) {
            throw new ObjectNotFoundException(id, 'Release')
        }
        return release
    }

    static Timestamp findLastUpdatedSprint(Release release) {
        executeQuery(
                """SELECT max(sprint.lastUpdated)
                   FROM Release release
                   INNER JOIN release.sprints as sprint
                   WHERE release = :release""", [release: release]).first() as Timestamp
    }

    int hashCode() {
        final int prime = 31
        int result = 1
        result = prime * result + ((!name) ? 0 : name.hashCode())
        result = prime * result + ((!parentProduct) ? 0 : parentProduct.hashCode())
        return result
    }

    boolean equals(Object obj) {
        if (this.is(obj))
            return true
        if (obj == null)
            return false
        if (getClass() != obj.getClass())
            return false
        final Release other = (Release) obj
        if (name == null) {
            if (other.name != null)
                return false
        } else if (name != other.name)
            return false
        if (parentProduct == null) {
            if (other.parentProduct != null)
                return false
        } else if (!parentProduct.equals(other.parentProduct))
            return false
        return true
    }

    Date getFirstDate() {
        if (sprints?.size() > 0) {
            return sprints.asList().last().endDate
        } else {
            return startDate
        }
    }

    boolean getClosable() {
        return state == STATE_INPROGRESS && (!sprints?.size() || sprints.asList().last().state == Sprint.STATE_DONE)
    }

    boolean getActivable() {
        return state == STATE_WAIT && (orderNumber == 1 || previousRelease && previousRelease.state == STATE_DONE)
    }

    Release getPreviousRelease() {
        return parentProduct.releases.findAll { it.orderNumber < orderNumber }?.max { it.orderNumber }
    }

    Release getNextRelease() {
        return parentProduct.releases.findAll { it.orderNumber > orderNumber }?.min { it.orderNumber }
    }

    Integer getMeanVelocity() {
        def doneSprints = sprints.findAll { it.state == Sprint.STATE_DONE }
        return doneSprints ? ((Integer) doneSprints.sum { it.velocity.toBigDecimal() }).intdiv(doneSprints.size()) : 0
    }

    def xml(builder) {
        builder.release(id: this.id) {
            state(this.state)
            endDate(this.endDate)
            todoDate(this.todoDate)
            doneDate(this.doneDate)
            startDate(this.startDate)
            orderNumber(this.orderNumber)
            lastUpdated(this.lastUpdated)
            dateCreated(this.dateCreated)
            inProgressDate(this.inProgressDate)
            name { builder.mkp.yieldUnescaped("<![CDATA[${this.name}]]>") }
            goal { builder.mkp.yieldUnescaped("<![CDATA[${this.goal ?: ''}]]>") }
            vision { builder.mkp.yieldUnescaped("<![CDATA[${this.vision ?: ''}]]>") }
            description { builder.mkp.yieldUnescaped("<![CDATA[${this.description ?: ''}]]>") }
            sprints() {
                this.sprints.each { _sprint ->
                    _sprint.xml(builder)
                }
            }
            features() {
                this.features.each { _feature ->
                    feature(uid: _feature.uid)
                }
            }
            attachments() {
                this.attachments.each { _att ->
                    _att.xml(builder)
                }
            }
            cliches() {
                this.cliches.each { _cliche ->
                    _cliche.xml(builder)
                }
            }
        }
    }
}
