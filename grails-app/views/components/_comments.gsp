%{--
  - Copyright (c) 2010 iceScrum Technologies.
  -
  - This file is part of iceScrum.
  -
  - iceScrum is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Lesser General Public License as published by
  - the Free Software Foundation, either version 3 of the License.
  -
  - iceScrum is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU General Public License for more details.
  -
  - You should have received a copy of the GNU Lesser General Public License
  - along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
  --}%

<g:set var="poOrSm" value="${request.scrumMaster || request.productOwner}"/>
<g:set var="comments" value="${commentable.comments}"/>

<ul id="comments-list" class="list-comments">
   <g:if test="${!comments || comments.size() == 0}">
    <li class="panel-box-empty">
      ${noComment}
    </li>
  </g:if>
  <g:each in="${comments}" var="comment" status="i">
      <g:render template="/components/comment"
                plugin="icescrum-core"
                model="[last:comments.size() == (i + 1),comment:comment, noEscape:noEscape, commentable:commentable, product:product, moderation:true, access:poOrSm, user:user]"/>
  </g:each>
</ul>
<div id="addComment" class="addComment">
  <g:render template="/components/commentEditor"
            plugin="icescrum-core"
            model="[commentable:commentable, hidden:true]"/>
</div>