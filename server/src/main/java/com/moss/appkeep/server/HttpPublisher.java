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
package com.moss.appkeep.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.jetty.handler.AbstractHandler;

import com.moss.appkeep.api.ComponentId;
import com.moss.appkeep.api.endorse.ComponentEndorsement;
import com.moss.appkeep.api.endorse.JarsignEndorsement;
import com.moss.appkeep.api.endorse.x509.X509CertId;
import com.moss.appkeep.server.data.Data;
import com.moss.launch.components.ComponentType;
import com.moss.saturn.lobstore.LobStore;
import com.sleepycat.je.LockMode;

/**
 * /by-component-id/uuid.TYPE?with-endorsement=jarsign:UUID
 */
public class HttpPublisher extends AbstractHandler{
	enum RootType {
		BY_COMPONENT_ID("by-component-id");

		private final String value;

		private RootType(String value) {
			this.value = value;
		}

		public static RootType read(String v){
			for(RootType next : RootType.values()){
				if(next.value.equals(v)){
					return next;
				}
			}
			return null;
		}

	}

	enum EndorsementType {
		JARSIGN{
			@Override
			public ComponentEndorsement parse(String text) {
				List<X509CertId> certs = new LinkedList<X509CertId>();
				for(String next : splitWell(text, ",")){
					certs.add(new X509CertId(next));
				}
				return new JarsignEndorsement(certs.toArray(new X509CertId[certs.size()]));
			}
		};

		public abstract ComponentEndorsement parse(String text);
	}

	private final Log log = LogFactory.getLog(getClass());
	private final Data data;
	private final LobStore lobs;
	private final KeepTool tool;
	
	public HttpPublisher(Data data, LobStore lobs, KeepTool tool) {
		super();
		this.data = data;
		this.lobs = lobs;
		this.tool = tool;
	}

	public void handle(
			String target, 
			HttpServletRequest request, 
			HttpServletResponse response, 
			int dispatch
	) throws java.io.IOException ,javax.servlet.ServletException {
		ComponentRequest cr = parse(request);

		if(cr!=null){
			final StoredComponent c = data.components.get(cr.componentId, null, LockMode.DEFAULT);
			
			if(c==null){
				completeWithError("No such component:" + cr.componentId, HttpServletResponse.SC_BAD_REQUEST, response);
			}
			
			if(c.type()!=cr.componentType){
				completeWithError("Invalid type for component: " + cr.componentType, HttpServletResponse.SC_BAD_REQUEST, response);
			}
			
			if(!c.isPublic()){
				completeWithError("ACCESS DENIED: component " + cr.componentId + " is not configured for public access", HttpServletResponse.SC_FORBIDDEN, response);
				return;
			}
			
			OutputStream out = response.getOutputStream();
			
			// FIND THE COMPONENT
			InputStream i = tool.getComponent(c, cr.endorsements);
			
			// WRITE THE DATA TO THE CLIENT
			try {
				byte[] buf = new byte[1024 * 100];
				for (int x = i.read(buf); x != -1; x = i.read(buf)) {
					out.write(buf, 0, x);
				}
				
				out.close();
			}finally{
				i.close();
			}
		}
	}

	private void completeWithError(String message, int code, HttpServletResponse response) throws IOException {
		response.setStatus(code);
		PrintWriter w = response.getWriter();
		w.write(message);
		w.close();
	}
	
	public static ComponentRequest parse(HttpServletRequest request){
		List<String> parts = splitWell(request.getPathInfo(), "/");

		RootType type = RootType.read(parts.get(0));

		if(type!=RootType.BY_COMPONENT_ID){
			return null;
		}

		ComponentId componentId;
		ComponentType componentType;
		{
			String fileName = parts.get(1);

			int pos = fileName.lastIndexOf('.');

			componentId = new ComponentId(fileName.substring(0, pos));
			componentType = ComponentType.valueOf(fileName.substring(pos+1).toUpperCase());
		}


//		System.out.println("Component ID: " + componentId);
//		System.out.println("Component Type: " + componentType);

		List<String> endorsementStrings = splitWell(request.getParameter("with-endorsements"), ";");

		List<ComponentEndorsement> endorsements = new LinkedList<ComponentEndorsement>();

		for(String next : endorsementStrings){
			int delimiterPos = next.indexOf(':');
			String typeString = next.substring(0, delimiterPos);
			String value = next.substring(delimiterPos+1);
//			System.out.println("Type " + typeString + ", value=" + value);

			EndorsementType endorsementType = EndorsementType.valueOf(typeString.toUpperCase());
			ComponentEndorsement e = endorsementType.parse(value);
			endorsements.add(e);
//			System.out.println("Endorsement " + e);
		}
		
		return new ComponentRequest(endorsements, componentId, componentType);
	}

	public static class ComponentRequest {
		private final List<ComponentEndorsement> endorsements;
		private final ComponentId componentId;
		private final ComponentType componentType;

		private ComponentRequest(List<ComponentEndorsement> endorsements,
				ComponentId componentId, ComponentType componentType) {
			super();
			this.endorsements = endorsements;
			this.componentId = componentId;
			this.componentType = componentType;
		}



	}
	private static List<String> splitWell(String text, String delimiter){
		List<String> parts = new LinkedList<String>();

		for(String next : text.split(delimiter)){
			if(next.trim().length()>0){
				parts.add(next);
			}
		}

		return parts;
	}
}
