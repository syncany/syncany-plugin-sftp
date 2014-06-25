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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.syncany.config.UserConfig;
import org.syncany.plugins.StorageException;
import org.syncany.plugins.UserInteractionListener;
import org.syncany.plugins.transfer.AbstractTransferManager;
import org.syncany.plugins.transfer.TransferManager;
import org.syncany.plugins.transfer.files.ActionRemoteFile;
import org.syncany.plugins.transfer.files.DatabaseRemoteFile;
import org.syncany.plugins.transfer.files.MultiChunkRemoteFile;
import org.syncany.plugins.transfer.files.RemoteFile;
import org.syncany.plugins.transfer.files.RepoRemoteFile;
import org.syncany.util.FileUtil;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.ChannelSftp.LsEntrySelector;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.UserInfo;

/**
 * Implements a {@link TransferManager} based on an SFTP storage backend for the
 * {@link SftpPlugin}. 
 * 
 * <p>Using an {@link SftpTransferSettings}, the transfer manager is configured and uses 
 * a well defined SFTP folder to store the Syncany repository data. While repo and
 * master file are stored in the given folder, databases and multichunks are stored
 * in special sub-folders:
 * 
 * <ul>
 *   <li>The <tt>databases</tt> folder keeps all the {@link DatabaseRemoteFile}s</li>
 *   <li>The <tt>multichunks</tt> folder keeps the actual data within the {@link MultiChunkRemoteFile}s</li>
 * </ul>
 * 
 * <p>All operations are auto-connected, i.e. a connection is automatically
 * established. 
 * 
 * @author Vincent Wiencek <vwiencek@gmail.com>
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class SftpTransferManager extends AbstractTransferManager {
	private static final Logger logger = Logger.getLogger(SftpTransferManager.class.getSimpleName());

	private JSch secureChannel;
	private Session secureSession;
	private ChannelSftp sftpChannel;

	private String repoPath;
	private String multichunksPath;
	private String databasesPath;
	private String actionsPath;

	public SftpTransferManager(SftpTransferSettings connection) {
		super(connection);

		this.secureChannel = new JSch();
		this.repoPath = connection.getPath();
		this.multichunksPath = connection.getPath() + "/multichunks";
		this.databasesPath = connection.getPath() + "/databases";
		this.actionsPath = connection.getPath() + "/actions";
		
		initKnownHosts();		
	}

	@Override
	public SftpTransferSettings getConnection() {
		return (SftpTransferSettings) super.getConnection();
	}

	@Override
	public void connect() throws StorageException {
		try {
			if (secureSession != null && secureSession.isConnected()) {
				return;
			}

			if (logger.isLoggable(Level.INFO)) {
				logger.log(Level.INFO, "SFTP client connecting to {0}:{1} ...", new Object[] { getConnection().getHostname(), getConnection().getPort() });
			}
			
			Properties properties = new Properties();
			properties.put("StrictHostKeyChecking", "ask"); 
			
			secureSession = secureChannel.getSession(getConnection().getUsername(), getConnection().getHostname(), getConnection().getPort());

			secureSession.setConfig(properties);
			secureSession.setPassword(getConnection().getPassword());
			
			if (getConnection().getUserInteractionListener() != null) {
				secureSession.setUserInfo(new SftpUserInfo());
			}
			
			secureSession.connect();
			
			if (!secureSession.isConnected()) {
				logger.warning("SFTP: unable to connect to sftp host " + getConnection().getHostname() + ":" + getConnection().getPort());
			}

			sftpChannel = (ChannelSftp) secureSession.openChannel("sftp");
			sftpChannel.connect();
			
			if (!sftpChannel.isConnected()){
				logger.warning("SFTP: unable to connect to sftp channel " + getConnection().getHostname() + ":" + getConnection().getPort());
			}
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "SFTP client connection failed.", e);
			throw new StorageException(e);
		}
	}

	@Override
	public void disconnect() {
		if (sftpChannel != null){
			sftpChannel.quit();
			sftpChannel.disconnect();
		}
		
		if (secureSession != null){
			secureSession.disconnect();
		}
	}

	@Override
	public void init(boolean createIfRequired) throws StorageException {
		connect();

		try {
			if (!testTargetExists() && createIfRequired) {
				sftpChannel.mkdir(repoPath);
			}
			
			sftpChannel.mkdir(multichunksPath);
			sftpChannel.mkdir(databasesPath);
			sftpChannel.mkdir(actionsPath);
		}
		catch (SftpException e) {
			disconnect();
			throw new StorageException("Cannot create directory " + multichunksPath + ", or " + databasesPath, e);
		}
	}

	@Override
	public void download(RemoteFile remoteFile, File localFile) throws StorageException {
		connect();

		String remotePath = getRemoteFile(remoteFile);

		if (!remoteFile.getName().equals(".") && !remoteFile.getName().equals("..")){
			try {
				// Download file
				File tempFile = createTempFile(localFile.getName());
				OutputStream tempFOS = new FileOutputStream(tempFile);
	
				if (logger.isLoggable(Level.INFO)) {
					logger.log(Level.INFO, "SFTP: Downloading {0} to temp file {1}", new Object[] { remotePath, tempFile });
				}
	
				sftpChannel.get(remotePath, tempFOS);
	
				tempFOS.close();
	
				// Move file
				if (logger.isLoggable(Level.INFO)) {
					logger.log(Level.INFO, "SFTP: Renaming temp file {0} to file {1}", new Object[] { tempFile, localFile });
				}
	
				localFile.delete();
				FileUtils.moveFile(tempFile, localFile);
				tempFile.delete();
			}
			catch (SftpException | IOException ex) {
				disconnect();
				logger.log(Level.SEVERE, "Error while downloading file " + remoteFile.getName(), ex);
				throw new StorageException(ex);
			}
		}
	}

	@Override
	public void upload(File localFile, RemoteFile remoteFile) throws StorageException {
		connect();

		String remotePath = getRemoteFile(remoteFile);
		String tempRemotePath = getConnection().getPath() + "/temp-" + remoteFile.getName();

		try {
			// Upload to temp file
			InputStream fileFIS = new FileInputStream(localFile);

			if (logger.isLoggable(Level.INFO)) {
				logger.log(Level.INFO, "SFTP: Uploading {0} to temp file {1}", new Object[] { localFile, tempRemotePath });
			}

			sftpChannel.put(fileFIS, tempRemotePath);
			
			fileFIS.close();

			// Move
			if (logger.isLoggable(Level.INFO)) {
				logger.log(Level.INFO, "SFTP: Renaming temp file {0} to file {1}", new Object[] { tempRemotePath, remotePath });
			}

			sftpChannel.rename(tempRemotePath, remotePath);
		}
		catch (SftpException | IOException ex) {
			disconnect();
			logger.log(Level.SEVERE, "Could not upload file " + localFile + " to " + remoteFile.getName(), ex);
			throw new StorageException(ex);
		}
	}

	@Override
	public boolean delete(RemoteFile remoteFile) throws StorageException {
		connect();

		String remotePath = getRemoteFile(remoteFile);

		try {
			sftpChannel.rm(remotePath);
			return true;
		}
		catch (SftpException ex) {
			disconnect();
			logger.log(Level.SEVERE, "Could not delete file " + remoteFile.getName(), ex);
			throw new StorageException(ex);
		}
	}

	@Override
	public <T extends RemoteFile> Map<String, T> list(Class<T> remoteFileClass) throws StorageException {
		connect();

		try {
			// List folder
			String remoteFilePath = getRemoteFilePath(remoteFileClass);
			
			List<LsEntry> entries = listEntries(remoteFilePath + "/");
			
			// Create RemoteFile objects
			Map<String, T> remoteFiles = new HashMap<String, T>();

			for (LsEntry entry : entries) {
				try {
					T remoteFile = RemoteFile.createRemoteFile(entry.getFilename(), remoteFileClass);
					remoteFiles.put(entry.getFilename(), remoteFile);
				}
				catch (Exception e) {
					logger.log(Level.INFO, "Cannot create instance of " + remoteFileClass.getSimpleName() + " for file " + entry.getFilename() + "; maybe invalid file name pattern. Ignoring file.");
				}
			}

			return remoteFiles;
		}
		catch (SftpException ex) {
			disconnect();

			logger.log(Level.SEVERE, "Unable to list FTP directory.", ex);
			throw new StorageException(ex);
		}
	}

	private String getRemoteFile(RemoteFile remoteFile) {
		return getRemoteFilePath(remoteFile.getClass()) + "/" + remoteFile.getName();
	}

	private String getRemoteFilePath(Class<? extends RemoteFile> remoteFile) {
		if (remoteFile.equals(MultiChunkRemoteFile.class)) {
			return multichunksPath;
		}
		else if (remoteFile.equals(DatabaseRemoteFile.class)) {
			return databasesPath;
		}
		else if (remoteFile.equals(ActionRemoteFile.class)) {
			return actionsPath;
		}
		else {
			return repoPath;
		}
	}
	
	private List<LsEntry> listEntries(String absolutePath) throws SftpException{
		final List<LsEntry> result = new ArrayList<>();
		LsEntrySelector selector = new LsEntrySelector() {
			public int select(LsEntry entry) {
				if (!entry.getFilename().equals(".")
						&& !entry.getFilename().equals("..")) {
					result.add(entry);
				}
				return CONTINUE;
			}
		};

		sftpChannel.ls(absolutePath, selector);
		return result;
	}

	@Override
	public boolean testTargetCanWrite() {
		try {
			SftpATTRS stat = sftpChannel.stat(repoPath);
			
			if (stat.isDir()) {
				String tempRemoteFile = repoPath + "/syncany-write-test";
				File tempFile = File.createTempFile("syncany-write-test", "tmp");

				sftpChannel.put(new FileInputStream(tempFile), tempRemoteFile);
				sftpChannel.rm(tempRemoteFile);
				
				tempFile.delete();
				
				logger.log(Level.INFO, "testTargetCanWrite: Can write, test file created/deleted successfully.");
				return true;
			}
			else {
				logger.log(Level.INFO, "testTargetCanWrite: Can NOT write, target does not exist.");
				return false;				
			}
		}
		catch (Exception e) {
			logger.log(Level.INFO, "testTargetCanWrite: Can NOT write to target.", e);
			return false;
		}
	}

	@Override
	public boolean testTargetExists() {
		try {
			SftpATTRS attrs = sftpChannel.stat(repoPath);
		    boolean targetExists = attrs.isDir();
		    
		    if (targetExists) {
		    	logger.log(Level.INFO, "testTargetExists: Target does exist.");
				return true;
		    }
		    else {
		    	logger.log(Level.INFO, "testTargetExists: Target does NOT exist.");
				return false;
		    }
		} 
		catch (Exception e) {
			logger.log(Level.WARNING, "testTargetExists: Target does NOT exist, error occurred.", e);
		    return false;
		}
	}

	@Override
	public boolean testTargetCanCreate() {
		// Find parent path
		String repoPathNoSlash = FileUtil.removeTrailingSlash(repoPath);
		int repoPathLastSlash = repoPathNoSlash.lastIndexOf("/");
		String parentPath = (repoPathLastSlash > 0) ? repoPathNoSlash.substring(0, repoPathLastSlash) : "/";
		
		// Test parent path permissions
		try {
			SftpATTRS parentPathStat = sftpChannel.stat(parentPath);
			
			boolean statSuccessful = parentPathStat != null;
			boolean hasWritePermissions = statSuccessful && (parentPathStat.getPermissions() & 00200) != 0;
			
			if (hasWritePermissions) {
				logger.log(Level.INFO, "testTargetCanCreate: Can create target at " + parentPathStat);
				return true;
			}
			else {
				logger.log(Level.INFO, "testTargetCanCreate: Can NOT create target (statSuccessful = " + statSuccessful 
						+ ", hasWritePermissions = " + hasWritePermissions + ")");
				
				return false;
			}			
		}
		catch (SftpException e) {
			logger.log(Level.INFO, "testTargetCanCreate: Can NOT create target at " + parentPath, e);
			return false;
		}
	}

	@Override
	public boolean testRepoFileExists() {
		try {
			String repoFilePath = getRemoteFile(new RepoRemoteFile());
			SftpATTRS repoFileStat = sftpChannel.stat(repoFilePath);
			
			if (repoFileStat.isReg()) {
				logger.log(Level.INFO, "testRepoFileExists: Repo file exists at " + repoFilePath);
				return true;
			}
			else {
				logger.log(Level.INFO, "testRepoFileExists: Repo file DOES NOT exist at " + repoFilePath);
				return false;
			}
		}
		catch (Exception e) {
			logger.log(Level.INFO, "testRepoFileExists: Exception when trying to check repo file existence.", e);
			return false;
		}
	}

	private void initKnownHosts() {
		try {
			File userHostKeyFile = new File(UserConfig.getUserPluginsUserdataDir("sftp"), "known_hosts");
			
			if (!userHostKeyFile.exists()) {
				userHostKeyFile.createNewFile();
			}
			
			secureChannel.setKnownHosts(userHostKeyFile.getAbsolutePath());
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private class SftpUserInfo implements UserInfo {
		private UserInteractionListener userInteractionListener;
		
		public SftpUserInfo() {			
			this.userInteractionListener = getConnection().getUserInteractionListener();
		}

		@Override
		public String getPassphrase() {
			return null; // Not supported
		}

		@Override
		public String getPassword() {
			return null; // Not supported
		}

		@Override
		public boolean promptPassword(String message) {			
			logger.log(Level.WARNING, "SFTP Plugin tried to ask for a password. Wrong SSH/SFTP password? This is NOT SUPPORTED right now.");
			return false; // Do NOT let JSch ask for new password (if given password is wrong)
		}

		@Override
		public boolean promptPassphrase(String message) {
			logger.log(Level.WARNING, "SFTP Plugin tried to ask for a passphrase. This is NOT SUPPORTED right now.");
			return false; // Do NOT let JSch ask for passphrase
		}

		@Override
		public boolean promptYesNo(String message) {
			return userInteractionListener.onUserConfirm("SSH/SFTP Confirmation", message, "Confirm");
		}

		@Override
		public void showMessage(String message) {
			userInteractionListener.onShowMessage(message);
		}		
	}
}
