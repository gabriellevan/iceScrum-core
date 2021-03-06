/*
 * Copyright (c) 2015 iceScrum Technologies.
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
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 * Manuarii Stein (manuarii.stein@icescrum.com)
 */




package org.icescrum.core.domain

import org.hibernate.ObjectNotFoundException
import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.icescrum.plugins.attachmentable.interfaces.Attachmentable
import org.icescrum.core.domain.preferences.UserPreferences
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.domain.security.UserAuthority

class User implements Serializable, Attachmentable {

    static final long serialVersionUID = 813639032272976126L

    String lastName = "Doe"
    String firstName = "John"
    String username = ""
    String password = ""
    String email

    Date dateCreated
    Date lastUpdated
    Date lastLogin

    UserPreferences preferences
    String uid



    boolean enabled = true
    boolean accountExternal = false
    boolean accountExpired
    boolean accountLocked
    boolean passwordExpired

    static transients = ['locale']

    static hasMany = [
            teams: Team
    ]

    static belongsTo = [Team]

    static mapping = {
        cache true
        table 'icescrum2_user'
        password column: 'passwd'
        username index: 'username_index'
        preferences lazy: false
        teams cache: true
    }

    static constraints = {
        email(blank: false, email: true, unique: true)
        username(blank: false, unique: true)
        password(blank: false)
        lastName(blank: false)
        firstName(blank: false)
        lastLogin(nullable: true)
    }

    static findExceptTeam(Long id, term, params) {
        executeQuery(
                "SELECT DISTINCT u " +
                        "FROM org.icescrum.core.domain.User as u " +
                        "WHERE u.id != :uid and (lower(u.username) like lower(:term) or lower(u.firstName) like lower(:term) " +
                        "or lower(u.lastName) like lower(:term)) and u.id not in " +
                        "(SELECT DISTINCT u2.id FROM org.icescrum.core.domain.User as u2 " +
                        "INNER JOIN u2.teams as t " +
                        "WHERE t.id = :t) ", [uid: SCH.context.authentication.principal?.id, t: id, term: "%$term%"], params ?: [:])
    }

    static findUsersLike(term, exCurrentUser, showDisabled, params) {
        executeQuery("SELECT DISTINCT u " +
                "FROM org.icescrum.core.domain.User as u " +
                "WHERE ${showDisabled == false ? 'u.enabled = true and ' : ''} ${exCurrentUser ? 'u.id != ' + SCH.context.authentication.principal?.id + ' and ' : ''}" +
                "( lower(u.email) like lower(:term) " +
                "or lower(u.username) like lower(:term) " +
                "or lower(u.firstName) like lower(:term) " +
                "or lower(u.lastName) like lower(:term) " +
                "or lower(concat(u.firstName,' ', u.lastName)) like lower(:term)" +
                "or lower(concat(u.lastName,' ', u.firstName)) like lower(:term)) " +
                "ORDER BY u.username ASC",
                [term: "%$term%"], params ?: [:])
    }

    static User withUser(long id){
        User user = get(id)
        if (!user) {
            throw new ObjectNotFoundException(id, 'User')
        }
        return user
    }

    Set<Authority> getAuthorities() {
        UserAuthority.findAllByUser(this).collect { it.authority } as Set
    }

    boolean equals(obj) {
        if (this.is(obj))
            return true
        if (obj == null)
            return false
        if (!getClass().isAssignableFrom(obj.getClass()))
            return false
        User other = (User) obj
        if (username == null) {
            if (other.username != null)
                return false
        } else if (!username.equals(other.username))
            return false
        if (email == null) {
            if (other.email != null)
                return false
        } else if (!email.equals(other.email))
            return false
        return true
    }

    int hashCode() {
        return username.hashCode()
    }

    def beforeValidate() {
        //Create uid before first save object
        if (!this.id && !this.uid) {
            this.uid = (this.username + this.email).encodeAsMD5()
        }
    }

    Locale getLocale() {
        new Locale(*preferences.language.split('_', 3))
    }

    def xml(builder) {
        builder.user(uid:this.uid) {
            email(this.email)
            enabled(this.enabled)
            username(this.username)
            password(this.password)
            dateCreated(this.dateCreated)
            accountLocked(this.accountLocked)
            accountExpired(this.accountExpired)
            passwordExpired(this.passwordExpired)
            accountExternal(this.accountExternal)
            lastName { builder.mkp.yieldUnescaped("<![CDATA[${this.lastName}]]>") }
            firstName { builder.mkp.yieldUnescaped("<![CDATA[${this.firstName}]]>") }

            preferences.xml(builder)

            teams(){
                this.teams.each { _team ->
                    team(uid: _team.uid)
                }
            }
        }
    }
}
