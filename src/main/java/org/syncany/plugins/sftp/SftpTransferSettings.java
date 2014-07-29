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
import java.util.Map;

import org.syncany.plugins.PluginOptionSpec;
import org.syncany.plugins.PluginOptionSpec.ValueType;
import org.syncany.plugins.PluginOptionSpecs;
import org.syncany.plugins.StorageException;
import org.syncany.plugins.transfer.TransferSettings;

/**
 * The SFTP connection represents the settings required to connect to an
 * SFTP-based storage backend. It can be used to initialize/create an
 * {@link SftpTransferManager} and is part of the {@link SftpPlugin}.
 *
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Christian Roth <christian.roth@port17.de>
 */
public class SftpTransferSettings extends TransferSettings {
	private static final String NO_KEY = "none";

	private String hostname;
	private File privateKey;
	private String username;
	private String password;
	private String path;
	private int port;

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

	@Override
	public void init(Map<String, String> optionValues) throws StorageException {
		getOptionSpecs().validate(optionValues);
		this.hostname = optionValues.get("hostname");
		this.username = optionValues.get("username");
		this.password = optionValues.get("password");
		this.path = optionValues.get("path");
		this.port = Integer.parseInt(optionValues.get("port"));

		if (optionValues.get("privatekey") != null && !NO_KEY.equalsIgnoreCase(optionValues.get("privatekey"))) {
			this.privateKey = new File(optionValues.get("privatekey"));
			if (!this.privateKey.isFile() || !this.privateKey.canRead()) throw new StorageException("Not a valid privatekey");
		}
	}

	@Override
	public PluginOptionSpecs getOptionSpecs() {
		return new PluginOptionSpecs(
			new PluginOptionSpec("hostname", "Hostname", ValueType.STRING, true, false, null),
			new PluginOptionSpec("privatekey", "Privatekey Path", ValueType.STRING, false, false, NO_KEY),
			new PluginOptionSpec("username", "Username", ValueType.STRING, true, false, null),
			new PluginOptionSpec("password", "Password", ValueType.STRING, true, true, null),
			new PluginOptionSpec("path", "Path", ValueType.STRING, true, false, null),
			new PluginOptionSpec("port", "Port", ValueType.INT, false, false, "22")
			);
	}

	@Override
	public String toString() {
		return SftpTransferSettings.class.getSimpleName()
		+ "[hostname=" + hostname + ":" + port + ", username=" + username + ", path=" + path + "]";
	}
}
