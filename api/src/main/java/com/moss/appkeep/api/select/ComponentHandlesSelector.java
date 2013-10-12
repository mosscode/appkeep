/**
 * Copyright (C) 2013, Moss Computing Inc.
 *
 * This file is part of appkeep.
 *
 * appkeep is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * appkeep is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with appkeep; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 */
package com.moss.appkeep.api.select;

import java.io.Serializable;
import java.util.List;

import com.moss.launch.components.ComponentHandle;

/**
 * Identifies a component by the union of one or more ArtifactHandle instances
 */
@SuppressWarnings("serial")
public class ComponentHandlesSelector extends ComponentSelector implements Serializable {
	private ComponentHandle[] handles;
	
	public ComponentHandlesSelector(ComponentHandle ... handles) {
		if(handles==null) throw new NullPointerException();
		this.handles = handles;
	}
	
	public ComponentHandlesSelector(List<ComponentHandle> handles) {
		this(handles.toArray(new ComponentHandle[handles.size()]));
	}
	
	@Override
	public <T> T accept(ComponentSelectorVisitor<T> visitor) {
		return visitor.visit(this);
	}
	
	public ComponentHandle[] handles() {
		return handles;
	}
	
	@Override
	public String toString() {
		StringBuilder text = new StringBuilder(getClass().getSimpleName());
		text.append("\n  " + handles.length + " handles:");
		for(ComponentHandle next: handles){
			text.append("\n");
			text.append(next.getClass().getSimpleName());
			text.append(" :");
			text.append(next.toString());
		}
		return text.toString();
	}
}
