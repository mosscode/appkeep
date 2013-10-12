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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;

import org.joda.time.Duration;
import org.joda.time.Instant;

import com.moss.appkeep.api.AppkeepService;
import com.moss.appkeep.api.ComponentId;
import com.moss.appkeep.api.ComponentInfo;
import com.moss.appkeep.api.endorse.JarsignEndorsement;
import com.moss.appkeep.api.endorse.x509.X509CertId;
import com.moss.appkeep.api.maven.MavenGroupInfo;
import com.moss.appkeep.api.security.AnonDownloadGrantId;
import com.moss.appkeep.api.security.AnonDownloadToken;
import com.moss.appkeep.api.security.DownloadToken;
import com.moss.appkeep.api.security.SecurityException;
import com.moss.appkeep.api.security.UserAccountDownloadToken;
import com.moss.appkeep.api.select.ComponentHandlesSelector;
import com.moss.appkeep.api.select.ComponentSelector;
import com.moss.appkeep.api.select.DirectComponentSelector;
import com.moss.identity.tools.IdProover;
import com.moss.launch.components.BuildTimestampComponentHandle;
import com.moss.launch.components.ComponentHandle;
import com.moss.launch.components.ComponentType;
import com.moss.launch.components.MavenCoordinatesHandle;

public abstract class AbstractAppkeepServiceTest<T extends AppkeepService> extends TestCase {

	private final static byte[] DATA = new byte[]{
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
			0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 0x34, 0x35, 
	};
	
	private final X509CertId certId = X509CertId.random();
	
	private T s;
	private IdProover idProver;
	
	@Override
	protected final void setUp() throws Exception {
		s = getService();
		idProver = idProver();
	}
	
	@Override
	protected void tearDown() throws Exception {
		disposeService(s);
	}
	
	protected abstract T getService() throws Exception ;
	protected abstract IdProover idProver() throws Exception;
	protected abstract void disposeService(T s) throws Exception ;
	
	
	public final void testMultipleHandles() throws Exception {
		final BuildTimestampComponentHandle timestamp1 = new BuildTimestampComponentHandle(new Instant(400000));
		final BuildTimestampComponentHandle timestamp2 = new BuildTimestampComponentHandle(new Instant(554000));
		
		final MavenCoordinatesHandle mavenA = new MavenCoordinatesHandle("com.moss.test", "test-a", "0.0.1");
		final MavenCoordinatesHandle mavenB = new MavenCoordinatesHandle("com.moss.test", "test-b", "0.0.1");
		
		
		
		final ComponentId id1 = s.post(
				new ComponentHandle[]{
						mavenA,
						timestamp1
				},
				ComponentType.JAR,
				idProver.giveProof(),
				new ByteArrayInputStream(DATA)
		);
		
		final ComponentId id2 = s.post(
				new ComponentHandle[]{
						mavenA,
						timestamp2
				}, 
				ComponentType.JAR, 
				idProver.giveProof(), 
				new ByteArrayInputStream(DATA)
			);
		
		try{
			// TEST ABIGUOUS MAVEN
			s.post(
					new ComponentHandle[]{
							new MavenCoordinatesHandle("com.moss.test", "test-b", "0.0.1-SNAPSHOT")
					}, 
					ComponentType.JAR, 
					idProver.giveProof(), 
					new ByteArrayInputStream(DATA)
				);
		}catch(Throwable t){
			// expected
		}
			
			{
				ComponentInfo info = s.getInfo(new ComponentHandlesSelector(mavenA, timestamp1), new UserAccountDownloadToken(idProver.giveProof()));
				assertEquals(id1, info.id());
			}
			{
				ComponentInfo info = s.getInfo(new ComponentHandlesSelector(timestamp1), new UserAccountDownloadToken(idProver.giveProof()));
				assertEquals(id1, info.id());
			}
			{
				ComponentInfo info = s.getInfo(new ComponentHandlesSelector(mavenA, timestamp2), new UserAccountDownloadToken(idProver.giveProof()));
				assertEquals(id2, info.id());
			}
			{
				ComponentInfo info = s.getInfo(new ComponentHandlesSelector(mavenA), new UserAccountDownloadToken(idProver.giveProof()));
				assertEquals(id2, info.id());
			}
			{
				ComponentInfo info = s.getInfo(new ComponentHandlesSelector(timestamp2), new UserAccountDownloadToken(idProver.giveProof()));
				assertEquals(id2, info.id());
			}
		
			
			final ComponentId id3 = s.post(
					new ComponentHandle[]{
							mavenA,
							timestamp1
					}, 
					ComponentType.JAR, 
					idProver.giveProof(), 
					new ByteArrayInputStream(DATA)
				);
			
			final ComponentId id4 = s.post(
					new ComponentHandle[]{
							mavenB,
							timestamp1
					}, 
					ComponentType.JAR, 
					idProver.giveProof(), 
					new ByteArrayInputStream(DATA)
				);
			
			{
				ComponentInfo info = s.getInfo(new ComponentHandlesSelector(mavenA, timestamp1), new UserAccountDownloadToken(idProver.giveProof()));
				assertEquals(id3, info.id());
			}
			{
				ComponentInfo info = s.getInfo(new ComponentHandlesSelector(timestamp1), new UserAccountDownloadToken(idProver.giveProof()));
				assertEquals(id4, info.id());
			}
			{
				ComponentInfo info = s.getInfo(new ComponentHandlesSelector(mavenA, timestamp2), new UserAccountDownloadToken(idProver.giveProof()));
				assertEquals(id2, info.id());
			}
			{
				ComponentInfo info = s.getInfo(new ComponentHandlesSelector(mavenA), new UserAccountDownloadToken(idProver.giveProof()));
				assertEquals(id3, info.id());
			}
			{
				ComponentInfo info = s.getInfo(new ComponentHandlesSelector(timestamp2), new UserAccountDownloadToken(idProver.giveProof()));
				assertEquals(id2, info.id());
			}
			
			{
				ComponentInfo info = s.getInfo(new ComponentHandlesSelector(mavenB, timestamp2), new UserAccountDownloadToken(idProver.giveProof()));
				assertNull("There is nothing that matches both of these handles.", info);
			}
		
	}
	
	public final void testBasicUpload() throws Exception {

		ComponentHandle[] handles = new ComponentHandle[]{
				new MavenCoordinatesHandle("com.moss.test", "test-a", "0.0.1")
		};
		
		ComponentId id = s.post(handles, ComponentType.JAR, idProver.giveProof(), new ByteArrayInputStream(DATA));
		
		{
			ComponentInfo info = s.getInfo(new DirectComponentSelector(id), new UserAccountDownloadToken(idProver.giveProof()));
			
			assertEquals(id, info.id());
			assertEquals(DATA.length, info.length());
			assertEquals(ComponentType.JAR, info.type());
			assertEquals(3, info.handles().length); // maven + generated crc & md5
			
			File temp = File.createTempFile("fdfds", ".dat");
			
			copy(s.download(new DirectComponentSelector(id), new UserAccountDownloadToken(idProver.giveProof())), new FileOutputStream(temp));
			
			assertEquals(DATA.length, temp.length());
			
			MavenGroupInfo group = s.mavenGroupInfo("com.moss.test", idProver.giveProof());
			assertEquals("com.moss.test", group.name());
			assertEquals(0, group.subgroups().size());
			assertEquals(1, group.components().size());
			assertEquals(info, group.components().get(0));
		}
		
		
		
		{// TEST ANONYMOUS DOWNLOAD SECURITY
			ComponentSelector selector = new DirectComponentSelector(id);
			AnonDownloadToken token = s.grantAccess(
					Arrays.asList(new ComponentSelector[]{selector}), 
					new Duration(50000), 
					idProver.giveProof()
			);
			File temp = File.createTempFile("fdfds", ".dat");
			
			copy(s.download(selector, token), new FileOutputStream(temp));
			
			assertEquals(DATA.length, temp.length());
			
			ComponentInfo info = s.getInfo(new DirectComponentSelector(id), token);
			
			assertEquals(id, info.id());
			assertEquals(DATA.length, info.length());
			assertEquals(ComponentType.JAR, info.type());
			assertEquals(3, info.handles().length); // maven + generated crc & md5

			DownloadToken badToken = new AnonDownloadToken(AnonDownloadGrantId.random());
			try{
				s.download(selector, badToken);
				fail("Should have received a security exception");
			}catch(SecurityException e){
				// expected;
			}
			try{
				s.getInfo(new DirectComponentSelector(id), badToken);
				fail("Should have received a security exception");
			}catch(SecurityException e){
				// expected;
			}
		}
	}

	public final void testBasicZiconUpload() throws Exception {

		ComponentHandle[] handles = new ComponentHandle[]{
				new MavenCoordinatesHandle("com.moss.test", "test-a", "0.0.1", "zicon", "pear")
		};
		
		ComponentId id = s.post(handles, ComponentType.ZICON, idProver.giveProof(), new ByteArrayInputStream(DATA));
		
		{
			ComponentInfo info = s.getInfo(new DirectComponentSelector(id), new UserAccountDownloadToken(idProver.giveProof()));
			
			assertEquals(id, info.id());
			assertEquals(DATA.length, info.length());
			assertEquals(ComponentType.ZICON, info.type());
			assertEquals(3, info.handles().length); // maven + generated crc & md5
			
			File temp = File.createTempFile("fdfds", ".dat");
			
			copy(s.download(new DirectComponentSelector(id), new UserAccountDownloadToken(idProver.giveProof())), new FileOutputStream(temp));
			
			assertEquals(DATA.length, temp.length());
			
			MavenGroupInfo group = s.mavenGroupInfo("com.moss.test", idProver.giveProof());
			assertEquals("com.moss.test", group.name());
			assertEquals(0, group.subgroups().size());
			assertEquals(1, group.components().size());
			assertEquals(info, group.components().get(0));
		}
		
		
		
		{// TEST ANONYMOUS DOWNLOAD SECURITY
			ComponentSelector selector = new DirectComponentSelector(id);
			AnonDownloadToken token = s.grantAccess(
					Arrays.asList(new ComponentSelector[]{selector}), 
					new Duration(50000), 
					idProver.giveProof()
			);
			File temp = File.createTempFile("fdfds", ".dat");
			
			copy(s.download(selector, token), new FileOutputStream(temp));
			
			assertEquals(DATA.length, temp.length());
			
			ComponentInfo info = s.getInfo(new DirectComponentSelector(id), token);
			
			assertEquals(id, info.id());
			assertEquals(DATA.length, info.length());
			assertEquals(ComponentType.ZICON, info.type());
			assertEquals(3, info.handles().length); // maven + generated crc & md5

			DownloadToken badToken = new AnonDownloadToken(AnonDownloadGrantId.random());
			try{
				s.download(selector, badToken);
				fail("Should have received a security exception");
			}catch(SecurityException e){
				// expected;
			}
			try{
				s.getInfo(new DirectComponentSelector(id), badToken);
				fail("Should have received a security exception");
			}catch(SecurityException e){
				// expected;
			}
		}
	}
	public void testJarsignEndorsements() throws Exception {
		
		final ComponentHandle[] handles = new ComponentHandle[]{
				new MavenCoordinatesHandle("com.moss.test", "test", "0.0.1-SNAPSHOT"),
				new BuildTimestampComponentHandle(new Instant())
		};
		
		InputStream jar = getClass().getResourceAsStream("test.jar");
		assertNotNull("This test resource should be on the classpath", jar);
		
		ComponentId id = s.post(
				handles,
				ComponentType.JAR, 
				idProver.giveProof(), 
				jar
			);
		
		try {
			s.download(new DirectComponentSelector(id), new UserAccountDownloadToken(idProver.giveProof()), new JarsignEndorsement(certId()));
			fail("This should have failed becuase the endorsement has not yet been made");
		} catch (Exception e) {
			// expected
		}

		s.endorse(
				new ComponentSelector[]{
						new DirectComponentSelector(id)
				}, 
				new JarsignEndorsement(certId())
				);
		
		s.download(new DirectComponentSelector(id), new UserAccountDownloadToken(idProver.giveProof()), new JarsignEndorsement(certId()));
		
	}
	
	protected final X509CertId certId(){
		return certId;
	}
	
	private void assertEquals(ComponentInfo expected, ComponentInfo actual){
		assertEquals(expected.id(), actual.id());
		assertEquals(expected.length(), actual.length());
		assertEquals(expected.type(), actual.type());
		assertEquals(expected.handles().length, actual.handles().length);
		
		List<ComponentHandle> expectedHandles = new LinkedList<ComponentHandle>(Arrays.asList(expected.handles()));
		List<ComponentHandle> actualHandles = new LinkedList<ComponentHandle>(Arrays.asList(actual.handles()));
		
		Comparator<ComponentHandle>  c = new Comparator<ComponentHandle>() {
			
			public int compare(ComponentHandle o1, ComponentHandle o2) {
				String a = o1.getClass().getName();
				String b = o2.getClass().getName();
				if(a.equals(b)){
					return a.toString().compareTo(b.toString());
				}else{
					return a.compareTo(b);
				}
			}
		};

		Collections.sort(expectedHandles, c);
		Collections.sort(actualHandles, c);
		
		for(int x=0;x<expectedHandles.size();x++){
			ComponentHandle hExpected = expectedHandles.get(x);
			ComponentHandle hActual = actualHandles.get(x);
			
			assertEquals(hExpected, hActual);
		}
	}
	
	private void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[100*1024];
		for(int x=in.read(buffer);x!=-1;x=in.read(buffer)){
			out.write(buffer, 0, x);
		}
		in.close();
		out.close();
	}
}
