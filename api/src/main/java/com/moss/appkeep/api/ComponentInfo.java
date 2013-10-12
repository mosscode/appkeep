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
package com.moss.appkeep.api;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.joda.time.Instant;

import com.moss.joda.time.xml.InstantAdapter;
import com.moss.launch.components.BuildTimestampComponentHandle;
import com.moss.launch.components.CRC32ComponentHandle;
import com.moss.launch.components.ComponentHandle;
import com.moss.launch.components.ComponentType;
import com.moss.launch.components.MavenCoordinatesHandle;
import com.moss.launch.components.Md5ComponentHandle;

/**
 * Information about a component in the repository
 */
public class ComponentInfo implements Serializable {
	@XmlElement
	private ComponentId id;

	@XmlElements({
		@XmlElement(type=MavenCoordinatesHandle.class, name="maven-coordinates"),
		@XmlElement(type=CRC32ComponentHandle.class, name="crc-32-checksum"),
		@XmlElement(type=Md5ComponentHandle.class, name="md5-hash"),
		@XmlElement(type=BuildTimestampComponentHandle.class, name="build-timestamp")
	})
	private ComponentHandle[] handles;
	@XmlElement
	private ComponentType type;
	@XmlElement
	private long length;
	
	@XmlJavaTypeAdapter(InstantAdapter.class)
	@XmlElement
	private Instant whenAdded;
	
	
	@Deprecated ComponentInfo() {}


	public ComponentInfo(ComponentId id, ComponentHandle[] handles,
			ComponentType type, long length, Instant whenAdded) {
		super();
		this.id = id;
		this.handles = handles;
		this.type = type;
		this.length = length;
		this.whenAdded = whenAdded;
	}

	public ComponentId id() {
		return id;
	}
	
	public ComponentHandle[] handles() {
		return handles;
	}
	public long length() {
		return length;
	}
	public ComponentType type() {
		return type;
	}
	public Instant whenAdded() {
		return whenAdded;
	}
	

	@Override
	public String toString() {
		final String indent = "    ";
		
		StringBuilder text = new StringBuilder(type + "{\n");
		
		text.append(indent);
		text.append("id: " + id + "\n");
		text.append(indent);
		text.append("length: " + length + "\n");
		text.append(indent);
		text.append("whenAdded: " + whenAdded + "\n");
		
		for(ComponentHandle next : handles){
			text.append(indent);
			text.append("handle [");
			text.append(next.getClass().getSimpleName());
			text.append("]: ");
			text.append(next.toString());
			text.append('\n');
		}
		text.append("}");
		return text.toString();
	}
}
