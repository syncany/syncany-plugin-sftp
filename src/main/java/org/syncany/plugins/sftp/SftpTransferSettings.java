/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2014 Philipp C. Heckel <philipp.heckel@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.syncany.plugins.sftp;

import java.io.File;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.core.Validate;
import org.syncany.plugins.transfer.Encrypted;
import org.syncany.plugins.transfer.Setup;
import org.syncany.plugins.transfer.StorageException;
import org.syncany.plugins.transfer.TransferSettings;

/**
 * The SFTP connection represents the settings required to connect to an
 * SFTP-based storage backend. It can be used to initialize/create an
 * {@link SftpTransferManager} and is part of the {@link SftpTransferPlugin}.
 *
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Christian Roth <christian.roth@port17.de>
 */
public class SftpTransferSettings extends TransferSettings {
	@Element(name = "hostname", required = true)
	@Setup(order = 1, description = "Hostname")
	private String hostname;

	@Element(name = "username", required = true)
	@Setup(order = 2, description = "Username")
	private String username;

	@Element(name = "password", required = true)
	@Setup(order = 3, sensitive = true, description = "Password")
	@Encrypted
	private String password;

	@Element(name = "privateKey", required = false)
	@Setup(order = 4, description = "Private Key")
	private File privateKey;

	@Element(name = "path", required = true)
	@Setup(order = 5, description = "Path")
	private String path;

	@Element(name = "port", required = false)
	@Setup(order = 6, description = "Port")
	private int port = 22;

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public File getPrivateKey() {
		return privateKey;
	}

	public void setPrivateKey(File privateKey) {
		this.privateKey = privateKey;
	}

	@Validate
	public void validate() throws StorageException {
		if (privateKey != null) {
			if (!privateKey.isFile() || !privateKey.canRead()) {
				throw new StorageException("Not a valid privatekey file at " + privateKey);
			}
		}
	}

	@Override
	public String toString() {
		return SftpTransferSettings.class.getSimpleName()
		+ "[hostname=" + hostname + ":" + port + ", username=" + username + ", path=" + path + "]";
	}
}
