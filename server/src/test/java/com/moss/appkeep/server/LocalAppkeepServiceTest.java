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

import java.io.File;

import com.moss.appkeep.api.mirror.PeerId;
import com.moss.appkeep.server.config.AdministratorConfig;
import com.moss.appkeep.server.config.CertificateRegistration;
import com.moss.appkeep.server.config.ServerConfiguration;
import com.moss.appkeep.server.config.X509KeystoreCertReference;
import com.moss.appkeep.server.data.Data;
import com.moss.appkeep.server.security.PasswordCheckerImpl;
import com.moss.appkeep.server.security.UserAccount;
import com.moss.fskit.TempDir;
import com.moss.identity.simple.SimpleId;
import com.moss.identity.simple.SimpleIdIdToolPlugin;
import com.moss.identity.simple.SimpleIdProover;
import com.moss.identity.standard.Password;
import com.moss.identity.standard.PasswordProofRecipie;
import com.moss.identity.tools.IdProover;
import com.moss.identity.tools.IdTool;
import com.moss.saturn.lobstore.LobStore;
import com.moss.saturn.lobstore.impl.LocalDiskLobStore;

public class LocalAppkeepServiceTest extends AbstractAppkeepServiceTest<AppkeepServiceImpl> {
	private static final SimpleId ADMIN_LOGON = new SimpleId("mr-sys-admin");
	private static final Password ADMIN_PASSWORD = new Password("i-am-spock");
	
	private TempDir t;
	private Data data;
	private LobStore lobs;

	
	
	@Override
	protected void disposeService(AppkeepServiceImpl s) throws Exception {
		data.close();
		t.deleteRecursively();	
	}
	
	@Override
	protected AppkeepServiceImpl getService() throws Exception {
		t = TempDir.create();
		
		lobs = new LocalDiskLobStore(new File(t, "lob-data"));
		data = new Data(new File(t, "bdb-data"), new Long(10 * 1024 * 100 /*10mb*/));
		
		
		UserAccount user = new UserAccount(ADMIN_LOGON);
		user.setAdministrator(true);
		data.userAccounts.put(ADMIN_LOGON, user, null);
		data.passwords.put(ADMIN_LOGON.getName(), ADMIN_PASSWORD.getPassword(), null);
		
		ServerConfiguration config = new ServerConfiguration();
		
		config.bindAddress("127.0.0.1");
		config.publishAddress(config.bindAddress());
		config.id(PeerId.random());
		config.idProofRecipie(new PasswordProofRecipie(new SimpleId("myserviceid"), new Password("testing123")));
		config.administrators().add(new AdministratorConfig(new PasswordProofRecipie(ADMIN_LOGON, ADMIN_PASSWORD)));

		config.certificates().add(
				new CertificateRegistration(
						certId(), 
						"my cert", 
						new X509KeystoreCertReference(
								new File("src/test/resources/com/moss/appkeep/server/test.ks"),
								"test",
								"testtest",
								"testtest"
						)
				)
			);
		
		IdTool idTool = new IdTool(
				new SimpleIdIdToolPlugin(new PasswordCheckerImpl(config, data))
			);
		return new AppkeepServiceImpl(data, lobs, null, config, null, null, idTool);
	}

	@Override
	protected IdProover idProver() throws Exception {
		return new SimpleIdProover(ADMIN_LOGON, ADMIN_PASSWORD);
	}
}
